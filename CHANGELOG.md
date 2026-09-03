# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.14] — 2026-09-03

### Added
- **Annotated Source in the web app.** The result panel now has two tabs,
  *Findings* and *Annotated Source*; the latter shows the original file as a
  grid with every finding painted on its cell — the same join the desktop tab
  and the Excel sheet use. Cells carry the finding text as a tooltip, "only
  rows / columns with findings" filters and 200-row paging keep the DOM small,
  and a *Source* button (or a double-click) on a finding row jumps to its
  cell. Server side, `ValidationOrchestrator` writes a gzip JSON artefact
  (`AnnotatedSourceJson`, core) next to the Excel report under the same id;
  `GET /api/annotated-source/{id}` serves it repeatedly until the report's
  5-minute TTL, and the XLSX download no longer deletes it. Files over
  `FINDATEX_WEB_ANNOTATED_SOURCE_MAX_ROWS` / `_MAX_CELLS` (20 000 / 2 M) skip
  the artefact — the response says `annotatedSourceAvailable=false` and the
  tab points at the Excel sheet.
- **Collapsible web layout.** The input column (Input + Notes) folds into a
  slim rail so the wide findings table gets the full viewport; Scores, Per
  Fund and Notes fold individually. All states are remembered per browser
  (`CollapsibleSection`, `usePersistedBoolean`). Manual only — no auto-collapse
  after a run.
- **Public `/help` page.** The Help markdown (`HELP.md`) is now also
  server-rendered at `/help` (Commonmark, same renderer as `/rules`), listed
  in the sitemap and linked from the footer and the rules pages. The FAQ
  structured data moved there.

### Changed
- **Full-width web layout.** Header, main and footer drop the 1600 px cap;
  the input column is 320 px and left-aligned.
- **External validation is on for the public instance.** The Cloud Run deploy
  workflow set `FINDATEX_WEB_EXTERNAL_ENABLED=false` on every release, which
  is why the "Enable GLEIF / OpenFIGI online checks" switch had vanished from
  the web UI. It is now `true`, with the OpenFIGI key mounted from Secret
  Manager (`findatex-openfigi-key`, a documented prerequisite). The per-run
  toggle still defaults to off.
- **Landing copy moved into Help.** The ~220 lines of static text under the
  validator (what the templates are, privacy, scoring, FAQ) left
  `index.html` and were merged into `HELP.md` / `ABOUT.md`, so the start page
  is the validator alone; search engines get the same text at `/help`.

- **Animated desktop walkthroughs in the README** (`docs/screenshots/desktop-*.gif`):
  validate a file, work with the results, batch mode. Each step is captioned
  and the view scrolls to the control it talks about. They are recorded, not
  hand-made: `tools/demo/DesktopDemoRecorder.java` drives the shaded jar under
  Xvfb through the JavaFX `Robot` and dumps frames plus a manifest,
  `build_demo_gif.py` paints caption, pointer and highlight and encodes
  through ffmpeg; `record_desktop_demo.sh` wires it up in an isolated HOME so
  no usage event is posted. `web-app-demo.gif` is re-recorded the same way
  (`record_web_demo.sh` + Playwright against a locally started jar) — the old
  one still showed V7.0 as default, a 10/h quota and a header without the
  rating and the offline-app link. The README also gained the public rule reference
  and the star rating, and the web steps say *Validate*, which is what the
  button reads.
- **A download link for the offline desktop build in the header**
  (`DesktopDownloadLink`, between the quota badge and the GitHub link). "Fund
  data must not be uploaded to a third party" is the objection that keeps much
  of the target audience from using the web app at all, and the answer to it
  was reachable only from a note below the form or once the quota was already
  exhausted. Hidden when `FINDATEX_WEB_DESKTOP_DOWNLOAD_URL` is empty, like
  every other optional action.

### Changed
- **The desktop export menu reads English** ("Export Excel report…", "One
  report per file…", "Combined report…", "Combined report + annotated
  source…") — the four strings were the last German labels in an otherwise
  English UI and showed up as such in the recorded walkthrough.
- **The star rating moved into the header**, immediately left of the quota
  badge, and the header bar is now **fixed** so it stays in reach while
  scrolling. The rating's comment box and Send button open as a popover under
  the stars (dismissable with Escape or a click outside), so the header stays
  one row high for everyone who never rates anything. `position: fixed` rather
  than `sticky`: a sticky element only sticks within its own containing block,
  which ends where the React app ends — the static landing content below
  `#root` sits outside it, so a sticky header would slide away there. A
  measured spacer reserves the header's height, since the bar wraps to two rows
  on narrow screens.

### Fixed
- **`index.html` was served with `public, immutable, max-age=86400`.** Quarkus
  applies that to everything under `META-INF/resources/`, which is right for
  the content-hashed bundle and wrong for the shell that names it: a returning
  visitor kept the previous app for up to a day after a deploy, and would get a
  blank page once the old assets were gone. `SpaCacheControlFilter` now sets
  `no-cache` on `/` and `/index.html` while the hashed assets keep their long
  cache — both asserted by `SeoResourcesTest`. Found by being hit by it while
  verifying the header change.

## [1.0.13] — 2026-08-31

### Added
- **The rule reference is public.** ~57k lines generated from the spec sheets
  existed only inside a modal (`GET /api/help/rules/{slug}`), where no search
  engine could reach it. It is now ~2000 server-rendered pages: `/rules`,
  `/rules/{slug}` per template version (scoring, profiles, general and
  cross-field rules) and `/rules/{slug}/field/{num}` per field (definition,
  flag per profile, codification, every rule that can fire on it). Each has its
  own title, description and canonical, renders without JavaScript, and links
  back into the validator. `RuleDocs` splits the generated Markdown on the
  generator's own structure, and `RuleDocsTest` fails if that shape moves
  instead of letting the pages go silently empty. Rendering uses commonmark-java
  — the same managed version the desktop Help dialog already used.
- **`sitemap.xml` is generated** (`SitemapResource`) and replaces the static
  file, which listed a single URL: superseded spec versions rank below current
  ones, since their field pages are near-identical to their successors'.
  A static `sitemap.xml` must never come back — the static-resource handler
  wins over the resource.
- **"No file at hand? Try an example".** A first-time visitor evaluating the
  tool rarely has a TPT/EET/EMT/EPT file to hand, and going to find one is
  where they left. One click now loads the bundled example for the selected
  template and validates it. The fixtures are the generator-driven files from
  `samples/`, mounted onto the web-app classpath by the build (single source of
  truth, still asserted by the `*ExampleSamplesTest` suites) and served from
  `GET /api/samples/{templateId}`; `GET /api/templates` advertises each one
  together with the **spec version it was generated for**, which the UI switches
  to — validating a fixture against another version reports findings that are
  artefacts of the mismatch. `SampleResourceTest` pushes every sample through
  `/api/validate` and fails if one stops producing findings, since a demo that
  finds nothing demonstrates nothing.
- **Landing-page content, visible to readers and crawlers.** What the four
  templates are, what happens to an uploaded file, how the quality score is
  computed, a seven-entry FAQ, and the "not an official FinDatEx tool"
  disclaimer — as plain HTML in `index.html` below the app root, so it is in
  the initial payload rather than behind a JavaScript render. The JSON-LD is
  now a `@graph` with `SoftwareApplication` **and `FAQPage`**, which only
  became legitimate once those answers are actually visible on the page;
  `SeoMetadataTest` compares the markup against the rendered copy and asserts
  the copy names the latest version of every template.
- **Visitor counter (`page_view`), so the funnel is finally visible.**
  `usage_event` counts validation runs, which on its own cannot distinguish
  "nobody found the site" from "people arrived and left without uploading" —
  two problems with opposite fixes. The SPA now fires one beacon per page load
  (`POST /api/page-view`, always 204) into a new `page_view` table via
  `PageViewService`; `tools/usage_report.py` prints both numbers together under
  **Traffic** with a `pct_validated` column, plus referrers, campaigns
  (`?utm_source=` / `?ref=`), pages and countries. Same shape as the existing
  stats path: same DB, inert without `FINDATEX_WEB_USAGE_DB_URL`, async insert
  with retry. Counted client-side on purpose — server-side counting would count
  crawlers, which at this traffic level would dominate the number; JS-executing
  bots are dropped by the new `BotDetector`. No cookie, no localStorage, no
  visitor or session id, no IP, no full referrer URL, no query strings, so no
  consent banner. `FINDATEX_WEB_PAGE_VIEW_RATE` (default 120/h per IP) tunes
  the limit. Schema and privacy notes in `docs/USAGE_STATS.md`.
- **`docs/SEO.md`** — one home for the findability thread: the four places that
  must agree on the canonical host, the CSP-hash trap, how to read the traffic
  funnel, the Google-Search-Console steps that have to be done by hand (DNS TXT
  verification), a post-deploy checklist, and the ranked open items.
- **Search-engine and link-preview metadata for the web app.** `index.html` now
  carries a descriptive `<title>`, a meta description, `<link rel="canonical">`,
  Open Graph and Twitter-card tags, and a schema.org `SoftwareApplication`
  JSON-LD block. Pasting the URL into LinkedIn/Teams/Slack renders a real card
  instead of a bare link. The card image (`public/og-image.png`, 1200×630) is
  generated by the new `tools/generate_og_image.py`, which reuses the app icon
  from `tools/generate_icon.py` so the two cannot drift apart.
- **`robots.txt` and `sitemap.xml`** as real static files under
  `web-app/src/main/resources/META-INF/resources/` (backend classpath, so they
  exist in `-P backend-only` builds too).
- **`FINDATEX_WEB_CANONICAL_HOST`** (`CanonicalHostFilter`): when set, GET/HEAD
  requests on any other hostname get a 301 to the canonical one. Off by default
  — self-hosted instances answer on whatever hostname they are deployed under.
  The Cloud Run deploy sets it to `www.findatex-validator.eu`.
- `SeoResourcesTest`, `SeoMetadataTest`, `CanonicalHostFilterTest` and
  `CanonicalHostMatchTest` (18 tests). `SeoMetadataTest` cross-checks the four
  places that have to agree on the canonical host and recomputes the CSP hash of
  the inline JSON-LD, which otherwise breaks silently in the browser.

### Changed
- **Cloud Run `min-instances` 0 → 1.** At this traffic level requests are far
  enough apart that nearly every visitor was the one paying for the cold start,
  looking at a blank page while a container came up. Costs ~8–10 EUR/month in
  idle billing. Applied to the running service as well as to the deploy
  workflow. While updating it, the scaling section of `docs/DEPLOY_CLOUDRUN.md`
  was corrected: it still described `max-instances=1` and `concurrency=80`,
  while the workflow has set 10 and 8 since the DoS baseline was introduced —
  including what that actually implies for the in-memory rate limit and report
  store.
- **Per-IP upload limit raised from 10/h to 30/h.** Evaluating the tool
  legitimately means several files in one sitting (one per template, a
  before/after fix); hitting the wall mid-evaluation was a conversion loss, not
  abuse. The concurrency cap and the 25 MB body limit are what actually bound a
  hostile client.
- **The desktop download is a real link now**, and points at the releases page
  (`…/releases`) rather than the repository root. The Notes panel used to say
  the desktop app "is available for download" without linking it, and the link
  only appeared once the quota was already exhausted.
- **CSP `script-src` gained a `sha256-` hash** for the single inline JSON-LD
  block. `'unsafe-inline'` was not an option and structured data cannot be
  loaded from an external file — search engines only read it inline.
- **Neon decommissioned.** The last Neon traces are gone now that the stats DB has
  run on the Hetzner VPS since 1.0.12: the superseded Neon password (version 1 of
  the GCP secret `findatex-usage-db-password`) is disabled, and the cold-start
  rationale in `application.properties`, `UsageStatsService`, `QuickFeedbackService`,
  `QuickFeedbackResource`, both retry tests and the docs now states the reason that
  actually applies — Cloud Run throttles an instance's CPU once the response is sent,
  so a fire-and-forget insert can stall and only complete on a later request.
  Retry counts, backoff and timeouts are unchanged.

### Fixed
- **The demo files never reached the container image.** `.dockerignore` excludes
  `samples/`, so the classpath mount added with "Try an example" found nothing,
  Maven skipped the missing resource directory without a word, and the image
  built green with the feature silently absent — `/api/samples/TPT` answered 404
  in production while every test passed locally. Fixed with a `.dockerignore`
  exception plus the matching `COPY`, and the app now logs a warning at startup
  when a declared demo file is not on the classpath, so the next silent loss is
  not silent.
- **`/robots.txt` and `/sitemap.xml` answered 200 with the SPA's HTML.**
  `SpaFallbackResource` swallowed every unmatched path, so crawlers got an
  invalid robots.txt and an unparseable sitemap. File-like paths (a last segment
  with an extension) now return a real 404 instead of the SPA shell, which also
  ends the unbounded soft-404s for stale asset URLs.

## [1.0.12] — 2026-08-25

### Changed
- **Usage-stats / quick-feedback database moved from Neon to the Postgres on the
  Hetzner VPS** (`findatex_stats`, TLS-only `hostssl` access for the `findatex`
  role, fail2ban jail). Started empty — no Neon rows were migrated. Cloud Run
  deploy workflow, `tools/usage_report.py` defaults and docs updated;
  acquisition timeout in prod lowered to 10 s (no serverless cold start any more).

### Fixed
- **Usage stats recorded `app_version = "dev"` for every web run** — `UsageEvent.detectAppVersion()`
  relied on the jar manifest's `Implementation-Version`, which neither the Quarkus fast-jar
  nor the core jar sets. Now resolves via `AppInfo.version()` (Maven-filtered properties,
  same source as the About dialog) with the manifest as fallback. Affects desktop too.

## [1.0.11] — 2026-08-25

### Fixed
- **About dialog (web + desktop) showed a hardcoded "Version 1.0.0".** `ABOUT.md`
  now carries a `{{version}}` placeholder that is filled at runtime from the
  Maven version (`quarkus.application.version` / `AppInfo.version()`).

## [1.0.10] — 2026-08-25

### Added
- **GitHub repo link + 1–5-star quick feedback in both UIs.** Header link
  (desktop button / SPA pill) via the single `AppInfo.githubUrl()` source;
  low-barrier star rating with optional comment — desktop "Rate this app"
  dialog relayed to `POST /api/quick-feedback`, SPA footer widget. Stored in
  the usage-stats DB (inert without `FINDATEX_WEB_USAGE_DB_URL`), no IP, no
  install id, own rate limit (`FINDATEX_WEB_QUICK_FEEDBACK_RATE`). See
  `docs/QUICK_FEEDBACK.md`.

### Fixed
- **Desktop version dropdown was unreadable — TPT V8.0 looked missing.** The
  ComboBox had no `StringConverter`, so it rendered the `TemplateVersion`
  record's `toString()` (truncated after `version=V…`, with spec file paths
  like `…20260526…` in the list). It now shows the label, e.g.
  "TPT V8.0 — 2026-05-26".
- Backslashes in finding values are escaped in the pre-filled GitHub issue
  table (Java builder + TS mirror), closing a CodeQL incomplete-sanitization
  finding.

## [1.0.9] — 2026-08-04

### Changed
- **Dependency refresh — zero open Dependabot alerts.** All pending Dependabot
  updates merged: jackson-databind 2.21.5 (closes 10 advisories, incl. two
  high-severity `PolymorphicTypeValidator` bypasses), undici 7.29.0, vite
  8.0.16 and postcss 8.5.25 (all npm advisories were build-toolchain-only),
  the grouped Maven/npm minor+patch updates, alpine 3.24 base image,
  actions/checkout v7 and setup-crane 0.7.
- **Migrated off APIs deprecated by those upgrades:** GeoIP2 5.x record
  accessors (`country()`/`isoCode()`) in `GeoIpService`, `Bandwidth.builder()`
  instead of `Bandwidth.classic`/`Refill` in `RateLimitService`,
  `CSVFormat.Builder.get()` in `CsvLoader`/`SourceMirror`. The unused
  deprecated `FindingEnricher.enrich(TptFile, List)` overload was removed —
  callers pass an explicit `FindingContextSpec`.

### Fixed
- **Flaky `UsageStatsReporterTest`.** The non-blocking assertion's timing
  bound was widened 2 s → 10 s: a blocking reporter would take minutes, so
  the bound still discriminates, but GC/CI load can no longer flake it.
- **Vite `configLoader: 'native'` warning** — `vite.config.ts` uses
  `import.meta.dirname` instead of the unsupported `__dirname`.

## [1.0.8] — 2026-06-07

### Fixed
- **Usage-stats runs were silently dropped on Neon cold start.** Neon
  (serverless) suspends compute when idle, so the first connection after an
  idle period exceeded Agroal's 5 s default acquisition timeout — the
  fire-and-forget `usage_event` insert failed and the run (and its
  `country_code`) was lost, leaving `tools/usage_report.py` totals frozen.
  `UsageStatsService` now retries the insert (3 attempts, linear backoff) and
  the acquisition timeout is raised to 30 s (override
  `FINDATEX_WEB_USAGE_DB_ACQUISITION_TIMEOUT`).

## [1.0.7] — 2026-06-06

### Fixed
- **GeoIP DB was missing from the 1.0.6 image.** BuildKit excludes secret
  *content* from a layer's cache key, so the `geoip` stage built once without
  the licence key (skip branch) was reused even after `MAXMIND_LICENSE_KEY` was
  added — shipping an image with an empty `/data/geoip` and `country_code`
  staying NULL. The stage's cache key is now tied to `GIT_COMMIT` and CI sets
  `no-cache-filters: geoip`, so the DB is (re-)downloaded on every build.

## [1.0.6] — 2026-06-06

### Added
- **Usage-stats country derivation (GeoIP).** The web image now bakes the
  MaxMind **GeoLite2-Country** database in at build time (downloaded via a
  BuildKit secret `maxmind_license_key`; the licence key is never committed),
  and sets `FINDATEX_WEB_GEOIP_DB` so `country_code` resolves from the request
  IP. No key ⇒ download skipped and the image still builds/boots with
  `country_code` NULL. Wired through `release.yml` (GitHub secret
  `MAXMIND_LICENSE_KEY`) and `docker-compose` (`.env`).

### Changed
- **Cloud Run deploy** now sets `PROXY_ADDRESS_FORWARDING=true` and
  `PROXY_ALLOW_X_FORWARDED=true` so the GeoIP lookup sees the real client IP
  from `X-Forwarded-For` instead of Google's internal front-end address.
  Trade-off (no `PROXY_TRUSTED_PROXIES` allowlist) documented in
  `docs/DEPLOY_CLOUDRUN.md` / `docs/USAGE_STATS.md`.

## [1.0.5] — 2026-05-26

### Added
- **TPT V8.0** (2026-05-26) bundled as the latest TPT version. V8 reuses V7's
  column layout and ISIN/LEI config; the only content changes are field 148
  renamed `Economic_sector_NACE2.1` → `Economic_sector_NACE` and two new
  conditional fields, `150_LTEI_Fund_Elligibility` and
  `151_Legislative_program_investment` (validated mechanically only).
- Native desktop installers (`.deb`, `.dmg`, `.msi`) plus no-admin portable
  bundles (`.zip`, `.tar.gz`) built automatically for Linux x64, Windows x64,
  macOS Intel and macOS Apple Silicon on every `v*` tag push, attached to
  the GitHub Release. Built via `jpackage` with a slim runtime generated by
  `jlink` — end users no longer need a JDK installed.

### Changed
- `package/jpackage.{sh,bat}`: vendor switched from "TPT Validator" to
  "Karl Kauc" (matches the repo owner). Both scripts now accept
  `APP_VERSION`, `APP_VENDOR` and `PACKAGE_TYPE` env overrides;
  `PACKAGE_TYPE=app-image` produces the portable layout used by the
  no-admin archives. Required JDK modules are computed from the shaded
  jar via `jdeps` so the runtime image stays minimal.

### Removed
- **TPT V6.0** (2022-03-14) is no longer bundled — superseded by V7.0/V8.0.
  Its spec, manifest and generated rule reference were dropped.

### Fixed
- _Nothing yet._

## [1.0.0] — 2026-04-28

First public release.

### Added

- Validation core for four FinDatEx templates: **TPT** (V6, V7), **EET**
  (V1.1.2, V1.1.3), **EMT** (V4.2, V4.3), **EPT** (V2.0, V2.1).
- Manifest-driven spec loader so new template versions are added by
  dropping an XLSX + sibling `*-info.json` into
  `core/src/main/resources/spec/`.
- Two delivery modes from one validation core:
  - **JavaFX desktop app** — files never leave the user's machine.
  - **Quarkus + React web app** — Docker-deployable, no login,
    rate-limited (per-IP token bucket + concurrency cap), auto-deletes
    uploads and reports.
- Optional external validation against **GLEIF** (LEI) and **OpenFIGI**
  (ISIN); off by default, supports system + manual NTLM proxies.
- Excel quality report with five sheets (`Summary`, `Scores`,
  `Findings`, `Field Coverage`, `Per Position`) and an *Annotated
  Source* tab with cell-level highlights and comments.
- Profile-aware quality scoring with a four-category weighted overall
  score (mandatory 40 / format 20 / closed-list 15 / cross-field 15 /
  profile-completeness avg 10).
- ~25 cross-field rules for TPT (SCR delivery, weight sums, NAV,
  custodian pair, dates, conditional XF-16..XF-25). EET/EMT/EPT rule
  sets are mechanical-only (presence + format + codification +
  spec-explicit conditional presence) — anything regulatory is
  explicitly DEFERRED.
- End-user `HELP.md` and a technical `README.md` (English-only UIs).
- Apache-2.0 license; CI workflow with xvfb-run JavaFX tests, JaCoCo
  coverage, and a Docker smoke build.

[Unreleased]: https://github.com/karlkauc/findatex-validator/compare/v1.0.14...HEAD
[1.0.14]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.14
[1.0.13]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.13
[1.0.12]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.12
[1.0.11]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.11
[1.0.10]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.10
[1.0.9]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.9
[1.0.8]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.8
[1.0.7]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.7
[1.0.6]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.6
[1.0.0]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.0
