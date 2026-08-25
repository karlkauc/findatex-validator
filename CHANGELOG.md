# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- _Nothing yet._

### Changed
- _Nothing yet._

### Fixed
- _Nothing yet._

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

[Unreleased]: https://github.com/karlkauc/findatex-validator/compare/v1.0.10...HEAD
[1.0.10]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.10
[1.0.9]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.9
[1.0.8]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.8
[1.0.7]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.7
[1.0.6]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.6
[1.0.0]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.0
