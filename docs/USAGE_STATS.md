# Anonymous usage statistics (opt-out)

The validator can report **aggregate-only** run statistics so development can be
prioritised by real usage (which templates/versions, success/error rates,
performance). It is **opt-out** (default on) and can be disabled per
installation. **No instance data ever leaves the machine**: no files, file
names, paths, fund names, ISIN/LEI/codes, cell values, finding messages/values,
user/host name, MAC, or exact OS version. The raw client **IP is never stored
or logged** — the server derives only an ISO country code from it.

## Architecture

```
Desktop:  validation done → UsageStatsReporter.report()  (enqueue, returns at once)
            → daemon thread → POST /api/usage-stats  (X-Usage-Token)
Web:      ValidationOrchestrator end → UsageStatsService.record(source=web)
Both:     → single-thread JDBC INSERT into Neon Postgres
Any failure/timeout → silently dropped (DEBUG log); the user never notices.
```

The JavaFX app never holds DB credentials; it only POSTs to the web app. The
web app is the sole DB writer.

## What is collected

One row per validation run (single file or one folder-batch):

| Field | Meaning |
|---|---|
| `event_id`, `received_at` | server-assigned (PK, `now()`) |
| `client_event_at` | client timestamp of the run |
| `install_id` | random UUID in `settings.json` (no PII); web uses the all-zero sentinel |
| `source` | `desktop` \| `web` |
| `app_version`, `os_name` | build version; OS **family only** (`Windows`/`Mac`/`Linux`/`Other`) |
| `template_id`, `template_version` | e.g. `TPT`, `V7` |
| `profiles` | profile **codes** only |
| `mode`, `file_count`, `row_count` | `single`/`batch`; counts only |
| `error_count`, `warning_count`, `info_count` | findings by severity |
| `overall_score` | OVERALL scaled to 0–100 (2 decimals) |
| `duration_ms` | measured run time |
| `external_enabled` | whether GLEIF/OpenFIGI ran |
| `rule_ids` | triggered rule IDs only (e.g. `XF-16`) — never values |
| `country_code` | ISO-3166-1 alpha-2, derived server-side from the request IP; `NULL` if unknown |

**Never collected:** file names/paths, fund names, ISIN/LEI/codes, cell
values, `Finding.message()/value()`, raw IP, user/host name, MAC, exact OS
version.

## Schema (run once in Neon — the app never issues DDL)

```sql
CREATE TABLE usage_event (
  event_id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  received_at      timestamptz NOT NULL DEFAULT now(),
  client_event_at  timestamptz,
  install_id       uuid        NOT NULL,
  source           text        NOT NULL CHECK (source IN ('desktop','web')),
  app_version      text,
  os_name          text,
  template_id      text        NOT NULL,
  template_version text        NOT NULL,
  profiles         text[]      NOT NULL DEFAULT '{}',
  mode             text        NOT NULL CHECK (mode IN ('single','batch')),
  file_count       int         NOT NULL DEFAULT 1,
  row_count        int,
  error_count      int,
  warning_count    int,
  info_count       int,
  overall_score    numeric(5,2),
  duration_ms      int,
  external_enabled boolean,
  rule_ids         text[]      NOT NULL DEFAULT '{}',
  country_code     text
);
CREATE INDEX idx_usage_received_at ON usage_event (received_at);
CREATE INDEX idx_usage_template    ON usage_event (template_id, template_version);
CREATE INDEX idx_usage_install     ON usage_event (install_id);
CREATE INDEX idx_usage_country     ON usage_event (country_code);
```

`gen_random_uuid()` is built in on Postgres ≥ 13 (Neon).

## Configuration (all env-overridable; feature off until set)

Desktop (`settings.json` → `usageStats`, plus env):
- `enabled` — opt-out flag (default `true`)
- `installId` — generated + persisted automatically
- `endpointUrl` — web ingest URL; blank disables the sender
- `FINDATEX_USAGE_TOKEN` — embedded ingest token; blank disables the sender

Web (`application.properties` / env):
- `FINDATEX_WEB_USAGE_DB_URL` / `_USER` / `_PASSWORD` — Neon JDBC
  (`...?sslmode=require`). **Empty ⇒ feature inert, app still boots.**
- `FINDATEX_WEB_USAGE_STATS_INGEST_TOKEN` — required for ingest; empty ⇒
  endpoint accepts-and-discards (logged once at startup)
- `FINDATEX_WEB_USAGE_STATS_RATE` — per-IP `/api/usage-stats` limit (default 60/h)
- `FINDATEX_WEB_GEOIP_DB` — path to a MaxMind **GeoLite2-Country.mmdb**;
  empty/missing ⇒ `country_code` is `NULL` (no boot failure)

### GeoLite2 database

The MMDB is **not** committed (MaxMind GeoLite2 EULA forbids unattributed
redistribution). Obtain it with a free MaxMind licence key, place it on the
container (e.g. via the image build), and point `FINDATEX_WEB_GEOIP_DB` at it.
Attribution: *“This product includes GeoLite2 data created by MaxMind,
available from https://www.maxmind.com.”*

## Abuse protection

- Static shared `X-Usage-Token` (constant-time compared) — wrong/missing ⇒ 401.
- Dedicated per-IP Bucket4j limit on `POST /api/usage-stats` (default 60/h).
- Existing 25 MB body limit ⇒ 413.
- Endpoint returns **202** immediately; insert is async fire-and-forget.
  Malformed JSON ⇒ 202 (DEBUG log), never 5xx.

## Operations (project-independent — SQL + psql)

Connect: `psql "postgresql://…neon…/db?sslmode=require"`.

```sql
-- Runs per day & template
SELECT received_at::date AS day, template_id, template_version,
       count(*) runs, round(avg(overall_score),1) avg_score
FROM usage_event GROUP BY 1,2,3 ORDER BY 1 DESC, 4 DESC;

-- Active installations (28 days)
SELECT count(DISTINCT install_id) active_installs
FROM usage_event
WHERE source='desktop' AND received_at > now() - interval '28 days';

-- Desktop vs web, single vs batch
SELECT source, mode, count(*), round(avg(duration_ms)) avg_ms
FROM usage_event GROUP BY 1,2 ORDER BY 1,2;

-- Runs per country
SELECT country_code, count(*) FROM usage_event
GROUP BY 1 ORDER BY 2 DESC;

-- Most-triggered rules
SELECT r AS rule_id, count(*) FROM usage_event, unnest(rule_ids) r
GROUP BY 1 ORDER BY 2 DESC LIMIT 25;

-- Error rate per template-version
SELECT template_id, template_version,
       round(avg(error_count),2) avg_errors,
       count(*) FILTER (WHERE error_count=0)*100.0/count(*) pct_clean
FROM usage_event GROUP BY 1,2 ORDER BY 1,2;

-- DB size / Free-tier watch (≤ 500 MB)
SELECT pg_size_pretty(pg_database_size(current_database())) db_size,
       count(*) rows, min(received_at), max(received_at) FROM usage_event;
```

Cleanup (free-tier 500 MB cap):

```sql
DELETE FROM usage_event WHERE received_at < now() - interval '12 months';
VACUUM (FULL, ANALYZE) usage_event;   -- brief lock
-- Emergency: TRUNCATE usage_event;
```

Export to a local Postgres before deleting:

```bash
pg_dump "postgresql://…neon…/db?sslmode=require" \
        -t usage_event --no-owner --no-acl -Fc -f usage_event.dump
pg_restore -d "postgresql://localhost/findatex_stats" usage_event.dump

# Incremental CSV
psql "postgresql://…neon…/?sslmode=require" -c \
 "\copy (SELECT * FROM usage_event WHERE received_at > '2026-01-01') TO 'inc.csv' CSV HEADER"
psql "postgresql://localhost/findatex_stats" -c \
 "\copy usage_event FROM 'inc.csv' CSV HEADER"
```

Recommended rhythm: check size monthly → export if needed → delete old rows.

## Privacy / GDPR

Only aggregate counts and a server-derived country are stored; no personal
data, no raw IP. The Settings dialog states what is/isn't sent and links here.
The DSGVO wording in the Settings tab and this document should be reviewed by
legal/SME before public deployment.
