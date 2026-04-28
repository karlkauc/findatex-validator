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

[Unreleased]: https://github.com/karlkauc/findatex-validator/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/karlkauc/findatex-validator/releases/tag/v1.0.0
