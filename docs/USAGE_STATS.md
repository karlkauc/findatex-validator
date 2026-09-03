# Anonymous usage statistics (opt-out)

The validator can report **aggregate-only** usage events so development can be
prioritised by real usage (which templates/versions, success/error rates,
performance, whether results get downloaded, how many people visit vs. upload).
It is **opt-out** (default on) and can be disabled per installation. **No
instance data ever leaves the machine**: no files, file names, paths, fund
names, ISIN/LEI/codes, cell values, finding messages/values, user/host name,
MAC, or exact OS version. File attributes are reduced to non-identifying
*classes* (format, size, naming pattern — see `FileNameShape`). The raw client
**IP is never stored or logged** — the server derives only an ISO country code
and, for web visitors, a daily-rotating hash from it.

Since **2026-09** every event kind lands in the one `usage_event` table, told
apart by `event_type`:

| `event_type` | Emitted by | Meaning |
|---|---|---|
| `validate` | desktop + web | one validation run (single file or folder batch). `status='ok'` when a report was produced; otherwise the failure class (`parse_error`, `template_mismatch`, `unsupported_type`, `bad_request`, `rate_limited`, `busy`, `error`) — never a message |
| `report_download` | desktop (export) + web (`GET /api/report/{id}`) | the result got used; `export_kind` = `xlsx` \| `per_file` \| `combined` \| `combined_annotated` |
| `sample_load` | web (`GET /api/samples/{t}`) | "Try an example" clicked (runs on that file additionally carry `is_sample=true`) |
| `page_view` | web SPA beacon | one page load (was the separate `page_view` table before) |

## Architecture

```
Desktop:  run / failure / export → UsageStatsReporter.report()  (enqueue, returns at once)
            → daemon thread → POST /api/usage-stats  (X-Usage-Token)
Web:      ValidationOrchestrator (runs, failures), ReportResource (downloads),
          SampleResource (sample loads), PageViewResource (page views),
          RateLimitFilter (429) → UsageStatsService.record…(ClientContext)
Request → ClientContextFactory: country (GeoIP), visitor_hash (VisitorHasher),
          device / os_name (UserAgentClassifier)  — the IP is consumed here, never stored
Both:     → single-thread JDBC INSERT into usage_event (Postgres, Hetzner VPS)
Any failure/timeout → silently dropped (DEBUG log); the user never notices.
```

The JavaFX app never holds DB credentials; it only POSTs to the web app. The
web app is the sole DB writer.

## What is collected

One row per event. Columns that do not apply to an event type stay `NULL`.

| Field | Meaning |
|---|---|
| `event_id`, `received_at` | server-assigned (PK, `now()`) |
| `event_type`, `status` | see the table above |
| `client_event_at` | client timestamp of the event |
| `install_id` | random UUID in `settings.json` (no PII); web uses the all-zero sentinel |
| `source` | `desktop` \| `web` |
| `app_version` | build version |
| `os_name` | OS **family only**: desktop from the JVM (`Windows`/`Mac`/`Linux`/`Other`), web from the browser User-Agent (`+ iOS`/`Android`). A web run used to record the *server's* OS — fixed 2026-09 |
| `java_major` | desktop only: Java feature version (21, 24 …) |
| `visitor_hash` | web only: `sha256(HMAC(secret, UTC-day) \| ip \| user-agent)[0:32]` — same visitor = same value within one day, different the next; not reversible, not linkable across days. Same scheme as xsd-viewer / xml-viewer |
| `user_agent` | web only, ≤ 255 chars, for the device/OS split and bot exclusion |
| `device` | web only: `desktop` \| `mobile` \| `bot` \| `unknown` (`BotDetector` + UA heuristics) |
| `template_id`, `template_version` | e.g. `TPT`, `V7.0`; `NULL` for page views and rejected uploads that never named one |
| `profiles` | profile **codes** only |
| `mode`, `file_count`, `row_count` | `single`/`batch`; counts only (`file_count` = reports written for `report_download`, 0 for page views) |
| `error_count`, `warning_count`, `info_count` | findings by severity |
| `overall_score` | OVERALL scaled to 0–100 (2 decimals) |
| `duration_ms` | measured run time |
| `external_enabled` | whether GLEIF/OpenFIGI ran |
| `rule_ids` | triggered rule IDs only (e.g. `XF-16`) — never values |
| `input_format`, `input_bytes`, `name_pattern` | derived file attributes: `xlsx`/`csv`/`mixed` (batch), size in bytes (sum for a batch), and whether the name follows the FinDatEx pattern — `dated_template` (`20260331_TPTV7_…`), `template_token` (a template token anywhere), `other`. The name itself is never sent |
| `is_sample` | web: the upload was the bundled sample file (matched by its download name) |
| `ext_lookups`, `ext_cache_hits`, `ext_duration_ms`, `ext_errors` | GLEIF/OpenFIGI phase: remote requests (distinct keys), cache hits, wall-clock, unavailable/cancelled phases |
| `export_kind` | `report_download` only, see above |
| `path`, `referrer_host`, `campaign` | `page_view` only — SPA route (query/fragment cut), referrer **host**, `?utm_source=`/`?ref=` slug |
| `country_code` | ISO-3166-1 alpha-2, derived server-side from the request IP; `NULL` if unknown |

**Never collected:** file names/paths, fund names, ISIN/LEI/codes, cell
values, `Finding.message()/value()`, error/exception text, raw IP, user/host
name, MAC, exact OS version, cookies or any persistent client id.

**Backward compatibility:** desktop builds from before 2026-09 keep posting the
old JSON (no `eventType`/`status`/`input`/`external`); the ingest defaults them
to `validate`/`ok`/`NULL`.

## Schema (run once in the target Postgres — the app never issues DDL)

```sql
CREATE TABLE usage_event (
  event_id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  received_at      timestamptz NOT NULL DEFAULT now(),
  client_event_at  timestamptz,
  install_id       uuid        NOT NULL,
  source           text        NOT NULL CHECK (source IN ('desktop','web')),
  event_type       text        NOT NULL DEFAULT 'validate'
                               CHECK (event_type IN ('page_view','validate','report_download','sample_load')),
  status           text        NOT NULL DEFAULT 'ok'
                               CHECK (status IN ('ok','unsupported_type','parse_error','template_mismatch',
                                                 'bad_request','rate_limited','too_large','busy','error')),
  app_version      text,
  os_name          text,
  java_major       int,
  visitor_hash     text,                      -- web only
  user_agent       text,                      -- web only, <= 255
  device           text        CHECK (device IS NULL OR device IN ('desktop','mobile','bot','unknown')),
  template_id      text,
  template_version text,
  profiles         text[]      NOT NULL DEFAULT '{}',
  mode             text        CHECK (mode IS NULL OR mode IN ('single','batch')),
  file_count       int         NOT NULL DEFAULT 1,
  row_count        int,
  error_count      int,
  warning_count    int,
  info_count       int,
  overall_score    numeric(5,2),
  duration_ms      int,
  external_enabled boolean,
  rule_ids         text[]      NOT NULL DEFAULT '{}',
  input_format     text        CHECK (input_format IS NULL OR input_format IN ('xlsx','csv','mixed')),
  input_bytes      bigint,
  name_pattern     text        CHECK (name_pattern IS NULL OR name_pattern IN ('dated_template','template_token','other')),
  is_sample        boolean,
  ext_lookups      int,
  ext_cache_hits   int,
  ext_duration_ms  int,
  ext_errors       int,
  export_kind      text        CHECK (export_kind IS NULL OR export_kind IN ('xlsx','per_file','combined','combined_annotated')),
  path             text,                      -- page_view
  referrer_host    text,                      -- page_view
  campaign         text,                      -- page_view
  country_code     text
);
CREATE INDEX idx_usage_received_at ON usage_event (received_at);
CREATE INDEX idx_usage_template    ON usage_event (template_id, template_version);
CREATE INDEX idx_usage_install     ON usage_event (install_id);
CREATE INDEX idx_usage_country     ON usage_event (country_code);
CREATE INDEX idx_usage_event_type_time ON usage_event (event_type, received_at);
CREATE INDEX idx_usage_visitor     ON usage_event (visitor_hash);
```

`gen_random_uuid()` is built in on Postgres ≥ 13.

### Migration 2026-09 (existing DB — run once, **before** deploying the new container)

Additive: widens `usage_event`, relaxes three `NOT NULL`s, folds the old
`page_view` rows in. Deploy order: **1)** this SQL on the Hetzner DB,
**2)** the usage dashboard (reads the new columns), **3)** the web container,
**4)** a desktop release whenever (old builds keep working).

```sql
BEGIN;
ALTER TABLE usage_event
  ADD COLUMN event_type      text NOT NULL DEFAULT 'validate',
  ADD COLUMN status          text NOT NULL DEFAULT 'ok',
  ADD COLUMN visitor_hash    text,
  ADD COLUMN user_agent      text,
  ADD COLUMN device          text,
  ADD COLUMN java_major      int,
  ADD COLUMN input_format    text,
  ADD COLUMN input_bytes     bigint,
  ADD COLUMN name_pattern    text,
  ADD COLUMN is_sample       boolean,
  ADD COLUMN ext_lookups     int,
  ADD COLUMN ext_cache_hits  int,
  ADD COLUMN ext_duration_ms int,
  ADD COLUMN ext_errors      int,
  ADD COLUMN export_kind     text,
  ADD COLUMN path            text,
  ADD COLUMN referrer_host   text,
  ADD COLUMN campaign        text;

ALTER TABLE usage_event
  ALTER COLUMN template_id      DROP NOT NULL,
  ALTER COLUMN template_version DROP NOT NULL,
  ALTER COLUMN mode             DROP NOT NULL;

-- auto-generated name; confirm with \d usage_event
ALTER TABLE usage_event DROP CONSTRAINT usage_event_mode_check;
ALTER TABLE usage_event
  ADD CONSTRAINT usage_event_mode_check
      CHECK (mode IS NULL OR mode IN ('single','batch')),
  ADD CONSTRAINT usage_event_event_type_check
      CHECK (event_type IN ('page_view','validate','report_download','sample_load')),
  ADD CONSTRAINT usage_event_status_check
      CHECK (status IN ('ok','unsupported_type','parse_error','template_mismatch',
                        'bad_request','rate_limited','too_large','busy','error')),
  ADD CONSTRAINT usage_event_device_check
      CHECK (device IS NULL OR device IN ('desktop','mobile','bot','unknown')),
  ADD CONSTRAINT usage_event_input_format_check
      CHECK (input_format IS NULL OR input_format IN ('xlsx','csv','mixed')),
  ADD CONSTRAINT usage_event_name_pattern_check
      CHECK (name_pattern IS NULL OR name_pattern IN ('dated_template','template_token','other')),
  ADD CONSTRAINT usage_event_export_kind_check
      CHECK (export_kind IS NULL OR export_kind IN ('xlsx','per_file','combined','combined_annotated'));

CREATE INDEX idx_usage_event_type_time ON usage_event (event_type, received_at);
CREATE INDEX idx_usage_visitor         ON usage_event (visitor_hash);

-- one-time backfill of the legacy page_view table (kept read-only afterwards;
-- drop it once the dashboard and tools/usage_report.py no longer reference it)
INSERT INTO usage_event (received_at, install_id, source, event_type, status,
                         path, referrer_host, campaign, country_code, file_count)
SELECT received_at, '00000000-0000-0000-0000-000000000000', 'web', 'page_view', 'ok',
       path, referrer_host, campaign, country_code, 0
FROM page_view;
COMMIT;

-- sanity: the two counts must match
SELECT (SELECT count(*) FROM page_view) legacy,
       (SELECT count(*) FROM usage_event WHERE event_type='page_view') folded;
```

## Configuration (all env-overridable; feature off until set)

Desktop (`settings.json` → `usageStats`, plus env):
- `enabled` — opt-out flag (default `true`)
- `installId` — generated + persisted automatically
- `endpointUrl` — web ingest URL; blank disables the sender
- `FINDATEX_USAGE_TOKEN` — embedded ingest token; blank disables the sender

Web (`application.properties` / env):
- `FINDATEX_WEB_USAGE_DB_URL` / `_USER` / `_PASSWORD` — JDBC URL of the
  stats DB (`...?sslmode=require`; prod: `findatex_stats` on the Hetzner VPS
  `tanzapp-prod`, see [Production database](#production-database)).
  **Empty ⇒ feature inert, app still boots.**
- `FINDATEX_WEB_USAGE_DB_ACQUISITION_TIMEOUT` — Agroal connection-acquisition
  timeout (default `30s`, prod sets `10s`). The inserts are fire-and-forget, so
  a slow acquisition would drop them silently; `UsageStatsService` additionally
  retries (3 attempts, linear backoff), because Cloud Run throttles CPU after
  the response and the async insert may only complete on the next request.
- `FINDATEX_WEB_USAGE_STATS_INGEST_TOKEN` — required for ingest; empty ⇒
  endpoint accepts-and-discards (logged once at startup)
- `FINDATEX_WEB_USAGE_STATS_RATE` — per-IP `/api/usage-stats` limit (default 60/h)
- `FINDATEX_WEB_GEOIP_DB` — path to a MaxMind **GeoLite2-Country.mmdb**;
  empty/missing ⇒ `country_code` is `NULL` (no boot failure)
- `FINDATEX_WEB_VISITOR_SALT_SECRET` — secret behind the daily visitor-hash
  salt (`HMAC-SHA256(secret, yyyy-MM-dd)`). Must be **identical on every
  instance** (Cloud Run runs several) or one visitor counts N times; prod
  keeps it in Secret Manager `findatex-visitor-salt`. Empty ⇒ per-process
  random salt: still works, but visitor counts are approximate and a WARN is
  logged once at first use

### GeoLite2 database

The MMDB is **not** committed (MaxMind GeoLite2 EULA forbids unattributed
redistribution). It is downloaded **at image build time** by the Dockerfile
`geoip` stage, gated on a BuildKit secret (`maxmind_license_key`), and copied
into the image at `/data/geoip/GeoLite2-Country.mmdb`; the image already sets
`FINDATEX_WEB_GEOIP_DB` to that path. No secret ⇒ download skipped, the path is
empty, and `country_code` stays NULL (the app still boots).

Get a free licence key at <https://www.maxmind.com> (Account → Manage License
Keys), then wire it per environment:

- **`docker compose`** — put `MAXMIND_LICENSE_KEY=…` in `.env`, then
  `docker compose build && docker compose up -d`. (The key is consumed only at
  build time; it never lands in a layer or the running container.)
- **CI / production image** — add a GitHub Actions **secret**
  `MAXMIND_LICENSE_KEY`; `release.yml` passes it to the build as the
  `maxmind_license_key` BuildKit secret.

> **Behind a proxy/LB (e.g. Cloud Run):** the DB alone is not enough — the app
> must also see the real client IP. Set `PROXY_ADDRESS_FORWARDING=true` and
> `PROXY_ALLOW_X_FORWARDED=true` (the Cloud Run deploy workflow already does).
> Without it the lookup gets the proxy's internal address and `country_code`
> stays NULL. See the proxy block in `application.properties` for the
> rate-limit-spoofing trade-off when no `PROXY_TRUSTED_PROXIES` allowlist is set.

Attribution: *“This product includes GeoLite2 data created by MaxMind,
available from https://www.maxmind.com.”*

## Page views (visitors vs. validations)

`usage_event` runs alone leave a quiet week ambiguous — nobody visited, or
visitors arrived and left without uploading anything? Those are two problems
with opposite fixes (promotion vs. a better landing page). Page views supply
the other half of that ratio, and since they share the table with runs and
downloads, the dashboard can build the funnel
**page_view → validate → report_download** per daily visitor hash.

One row per page load, `event_type='page_view'`, written by `PageViewService`
→ `UsageStatsService` from the beacon the SPA fires in `main.tsx`
(`POST /api/page-view`, always answers **204**). The beacon body is unchanged
(`path`, `referrer`, `campaign`); country, visitor hash, device and OS family
are derived from the request (`ClientContextFactory`).

| Field | Meaning |
|---|---|
| `path` | SPA route; query string and fragment are **cut off** before storing |
| `referrer_host` | **host only** of `document.referrer` (`www.` stripped, http/https only); `NULL` for direct traffic and for same-origin navigation |
| `campaign` | `?utm_source=` / `?ref=`, reduced to a `[a-z0-9._-]` slug — tells a LinkedIn post apart from organic traffic |
| `visitor_hash`, `device`, `os_name`, `user_agent`, `country_code` | as for every web event (see above) |

**Never collected:** no cookie, no localStorage, no persistent visitor or
session id, no fingerprint, no raw IP, no full referrer URL, no query strings.
The daily hash lets the dashboard count *distinct visitors per day* and follow
one visitor from page view to download **within that day**; it cannot be
reversed and cannot link two days. Client-side counting (rather than counting
HTML requests server-side) is deliberate: it keeps crawlers and uptime probes
out of a number that would otherwise be mostly bots at this traffic level. Bot
user agents that do execute JavaScript are dropped by `BotDetector`.

Legacy: before 2026-09 page views had their own table —

```sql
CREATE TABLE page_view (  -- LEGACY, read-only since the 2026-09 migration
  view_id bigserial PRIMARY KEY, received_at timestamptz NOT NULL DEFAULT now(),
  path text NOT NULL, referrer_host text, campaign text, country_code text);
```

— its rows were folded into `usage_event` by the migration above; drop it once
nothing reads it any more (`DROP TABLE page_view;`).

Config: no separate switch — the feature follows `FINDATEX_WEB_USAGE_DB_URL`
(empty ⇒ inert, endpoint still answers 204). `FINDATEX_WEB_PAGE_VIEW_RATE`
tunes the per-IP limit (default 120/h; deliberately generous — a real visitor
reloading must never be throttled).

`tools/usage_report.py` prints the funnel under **Traffic**: views and web runs
per day with a `pct_validated` column, plus visitors, referrers, campaigns,
pages and countries. The richer view (funnel per visitor, devices, file
formats, failures, external lookups) is the findatex profile of the usage
dashboard (`~/webdav/usage_statistics`).

## Abuse protection

- Static shared `X-Usage-Token` (constant-time compared) — wrong/missing ⇒ 401.
- Dedicated per-IP Bucket4j limit on `POST /api/usage-stats` (default 60/h).
- Existing 25 MB body limit ⇒ 413. Not counted as a `too_large` run: Quarkus
  rejects the request before any router/JAX-RS code runs (the `status` value
  exists for a future client-side beacon).
- Endpoint returns **202** immediately; insert is async fire-and-forget.
  Malformed JSON ⇒ 202 (DEBUG log), never 5xx.

## Operations (project-independent — SQL + psql)

Connect (from the VPS): `psql "postgresql://findatex:$(cat /home/deploy/findatex-db-password.txt)@127.0.0.1/findatex_stats"`
— or remotely with `…@62.238.116.11:5432/findatex_stats?sslmode=require`.

```sql
-- Events by type / status
SELECT event_type, status, source, count(*) FROM usage_event
GROUP BY 1,2,3 ORDER BY 1,2,3;

-- Runs per day & template (successful runs only)
SELECT received_at::date AS day, template_id, template_version,
       count(*) runs, round(avg(overall_score),1) avg_score
FROM usage_event WHERE event_type='validate' AND status='ok'
GROUP BY 1,2,3 ORDER BY 1 DESC, 4 DESC;

-- Active installations (28 days) + distinct web visitors today (no bots)
SELECT count(DISTINCT install_id) FILTER (WHERE source='desktop') active_installs,
       count(DISTINCT visitor_hash) FILTER (WHERE source='web' AND device IS DISTINCT FROM 'bot'
                                            AND received_at::date = current_date) web_visitors_today
FROM usage_event WHERE received_at > now() - interval '28 days';

-- Web funnel per day (page views → runs → downloads, by daily visitor hash)
SELECT received_at::date d,
       count(DISTINCT visitor_hash) FILTER (WHERE event_type='page_view') visitors,
       count(DISTINCT visitor_hash) FILTER (WHERE event_type='validate') validating,
       count(*) FILTER (WHERE event_type='report_download') downloads
FROM usage_event WHERE source='web' AND device IS DISTINCT FROM 'bot'
GROUP BY 1 ORDER BY 1 DESC LIMIT 14;

-- Desktop vs web, single vs batch
SELECT source, mode, count(*), round(avg(duration_ms)) avg_ms
FROM usage_event WHERE event_type='validate' GROUP BY 1,2 ORDER BY 1,2;

-- Devices & OS of web runs (the "everything is Linux" bug is gone)
SELECT device, os_name, count(*) FROM usage_event
WHERE source='web' GROUP BY 1,2 ORDER BY 3 DESC;

-- Input files: format, naming pattern, size
SELECT source, input_format, name_pattern, count(*) runs,
       round(percentile_cont(0.5) WITHIN GROUP (ORDER BY input_bytes)/1024) p50_kb,
       count(*) FILTER (WHERE is_sample) sample_runs
FROM usage_event WHERE event_type='validate' GROUP BY 1,2,3 ORDER BY 1, 4 DESC;

-- Failed runs by class
SELECT status, source, count(*) FROM usage_event
WHERE event_type='validate' AND status <> 'ok' GROUP BY 1,2 ORDER BY 3 DESC;

-- External lookups
SELECT source, count(*) runs, sum(ext_lookups) lookups, sum(ext_cache_hits) cache_hits,
       sum(ext_errors) errors FROM usage_event WHERE external_enabled GROUP BY 1;

-- Runs per country
SELECT country_code, count(*) FROM usage_event WHERE event_type='validate'
GROUP BY 1 ORDER BY 2 DESC;

-- Most-triggered rules
SELECT r AS rule_id, count(*) FROM usage_event, unnest(rule_ids) r
WHERE event_type='validate' GROUP BY 1 ORDER BY 2 DESC LIMIT 25;

-- Error rate per template-version
SELECT template_id, template_version,
       round(avg(error_count),2) avg_errors,
       count(*) FILTER (WHERE error_count=0)*100.0/count(*) pct_clean
FROM usage_event WHERE event_type='validate' AND status='ok' GROUP BY 1,2 ORDER BY 1,2;

-- DB size / Free-tier watch (≤ 500 MB)
SELECT pg_size_pretty(pg_database_size(current_database())) db_size,
       count(*) rows, min(received_at), max(received_at) FROM usage_event;
```

Cleanup (keep the table lean):

```sql
DELETE FROM usage_event WHERE received_at < now() - interval '12 months';
VACUUM (FULL, ANALYZE) usage_event;   -- brief lock
-- Emergency: TRUNCATE usage_event;
```

Export to a local Postgres before deleting:

```bash
pg_dump "postgresql://findatex:…@62.238.116.11:5432/findatex_stats?sslmode=require" \
        -t usage_event --no-owner --no-acl -Fc -f usage_event.dump
pg_restore -d "postgresql://localhost/findatex_stats" usage_event.dump

# Incremental CSV
psql "postgresql://findatex:…@62.238.116.11:5432/findatex_stats?sslmode=require" -c \
 "\copy (SELECT * FROM usage_event WHERE received_at > '2026-01-01') TO 'inc.csv' CSV HEADER"
psql "postgresql://localhost/findatex_stats" -c \
 "\copy usage_event FROM 'inc.csv' CSV HEADER"
```

Recommended rhythm: check size monthly → export if needed → delete old rows.

## Production database

Since 2026-08-25 the stats DB lives on the shared **PostgreSQL 18** of the
Hetzner VPS `tanzapp-prod` (62.238.116.11) — migrated from Neon (free-tier
compute quota exhausted). Started **empty**; no Neon rows were carried over.

- DB `findatex_stats`, role `findatex` (owner); password in
  `/home/deploy/findatex-db-password.txt` on the server and as
  Secret Manager `findatex-usage-db-password` (used by Cloud Run).
- Cloud Run has no fixed egress IP, so `pg_hba.conf` allows **only**
  `hostssl findatex_stats findatex 0.0.0.0/0 scram-sha-256` (TLS-only, this
  DB/role only); ufw opens 5432/tcp; fail2ban jail `postgresql` (5 failed
  logins / 10 min ⇒ 1 h ban; filter `/etc/fail2ban/filter.d/postgresql.conf`,
  relies on `log_line_prefix` containing `%h`).
- Backups: Hetzner VM snapshots (daily, 7 rolling) — no logical dump yet.

## Privacy / GDPR

Only aggregate counts, derived classes and a server-derived country are
stored; no personal data, no raw IP, no file names. Desktop rows carry a random
install id that is bound to nothing (no user, host or hardware). Web rows carry
a **daily-rotating visitor hash**: `sha256(HMAC(secret, day) | ip | user-agent)`
truncated to 32 hex chars — it cannot be reversed to the IP (keyed, salted,
rotated), and the same person yields a different value every day, so rows can
be grouped within a day (visitors, funnel) but not tracked over time. This is
the identical scheme the xsd-viewer / xml-viewer apps use. The Settings dialog
and the web landing copy state what is/isn't sent and link here.
The DSGVO wording in the Settings tab and this document should be reviewed by
legal/SME before public deployment.

## Related

The `quick_feedback` table (star ratings) shares this database and datasource —
see [QUICK_FEEDBACK.md](QUICK_FEEDBACK.md).
