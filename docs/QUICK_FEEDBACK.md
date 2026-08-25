# Quick feedback (1–5 star rating)

Users can rate the validator (1–5 stars, optional free-text comment) from the
**web app** (footer widget) and the **JavaFX desktop app** ("★ Rate this app"
in the header). It is deliberately low-barrier: no account, no e-mail, one
click on a star.

Not to be confused with the *"Report a false positive"* feature
(`feedback` package / `GET /api/feedback-config`), which opens a pre-filled
GitHub issue for a specific finding. Quick feedback (`quickfeedback` package /
`POST /api/quick-feedback`) is the star rating described here.

## What is collected — and what never

One row per submission:

| Field | Meaning |
|---|---|
| `feedback_id`, `received_at` | server-assigned (PK, `now()`) |
| `source` | `desktop` \| `web` (server-side clamped to this vocabulary) |
| `rating` | 1–5 |
| `comment` | optional free text, max 2000 chars (trimmed; blank → `NULL`) |
| `app_version` | desktop build version; `NULL` for web submissions |
| `template_id` | template the user had selected (e.g. `TPT`); optional |
| `country_code` | ISO-3166-1 alpha-2, derived server-side from the request IP; `NULL` if unknown |

**Never collected:** install id (feedback is deliberately *not* linkable to
usage events), raw IP (never stored or logged), file names/contents, e-mail,
user/host name. The comment text is stored but never written to logs.

## Architecture

```
Web:     React footer widget → POST /api/quick-feedback {rating, comment?, source, templateId?}
                             → QuickFeedbackResource (validate, rate-limit)
                             → QuickFeedbackService → async JDBC INSERT (Postgres)
                             → optimistic {status:"ok"} → UI message
Desktop: header "★ Rate this app" → QuickFeedbackDialog
                             → QuickFeedbackClient (core, proxy/NTLM-aware)
                             → POST same web endpoint → {status} → dialog message
Gating:  GET /api/quick-feedback-config {enabled} drives whether the SPA shows
         the widget. No DB configured ⇒ feature inert (POST → 503), widget hidden.
```

The response is **optimistic**: validation is synchronous (400 `invalid`,
503 `unavailable`, 429 rate-limited), but the INSERT runs asynchronously with
the same 3-attempt/1500 ms retry as usage stats — a slow DB connection (e.g. the former Neon cold start, 10–30 s)
must never block the response. A rating lost to a persistently dead DB is
acceptable by design.

## Status vocabulary

`com.findatex.validator.quickfeedback.QuickFeedbackStatus` is the single
source of truth; the JSON wire token is the lowercase name: `ok`, `invalid`,
`rate_limited`, `unavailable`. The React frontend mirrors the same vocabulary.

## Schema (run once in the target Postgres — the app never issues DDL)

Shares the usage-stats database (`FINDATEX_WEB_USAGE_DB_URL`,
see [USAGE_STATS.md](USAGE_STATS.md)):

```sql
CREATE TABLE quick_feedback (
  feedback_id  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  received_at  timestamptz NOT NULL DEFAULT now(),
  source       text        NOT NULL CHECK (source IN ('desktop','web')),
  rating       int         NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment      text,
  app_version  text,
  template_id  text,
  country_code text
);
CREATE INDEX idx_quick_feedback_received_at ON quick_feedback (received_at);
```

## Configuration

Web (env vars; defaults in `web-app/src/main/resources/application.properties`):

| Env var | Default | Meaning |
|---|---|---|
| `FINDATEX_WEB_USAGE_DB_URL` (+ `_USER`, `_PASSWORD`) | empty | shared datasource; empty ⇒ feature fully inert (503, widget hidden) |
| `FINDATEX_WEB_QUICK_FEEDBACK_RATE` | `5` | per-IP submissions/hour (Bucket4j, POST only; the config GET is unmetered) |

Desktop: **Settings → Feedback → "App rating / quick feedback"** holds the
endpoint base URL. Default is `https://www.findatex-validator.eu`, so the
feature works out of the box; **empty = disabled**. The desktop never holds DB
credentials — it only POSTs to the web app (same trust model as
newsletter/usage-stats).

## Operations (SQL via psql, same connection as usage stats)

```sql
-- Rating distribution
SELECT rating, count(*) FROM quick_feedback GROUP BY rating ORDER BY rating;

-- Average per month
SELECT date_trunc('month', received_at) AS month,
       round(avg(rating), 2) AS avg_rating, count(*) AS n
FROM quick_feedback GROUP BY 1 ORDER BY 1 DESC;

-- Recent comments
SELECT received_at, source, rating, template_id, comment
FROM quick_feedback WHERE comment IS NOT NULL
ORDER BY received_at DESC LIMIT 50;
```
