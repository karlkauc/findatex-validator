# FinDatEx Validator

Validator for [FinDatEx](https://findatex.eu) data-template files (`.xlsx`,
`.csv`). Produces a quality & conformance report against the relevant
template specification. Two ways to run it, sharing one validation core:

- **Desktop (JavaFX)** — for daily use at the asset manager. Files never
  leave the user's machine. Native installer builds (`.deb`, `.rpm`, `.dmg`,
  `.msi`) via jpackage.
- **Web (Quarkus + React, Docker)** — self-hosted container for users who
  can't install software locally or just need an occasional validation. No
  login. Anti-misuse: per-IP rate limit, body-size cap, concurrency cap,
  auto-delete of uploads + reports.

Each supported template lives in its own UI tab (desktop) or selectable tab
strip (web), with its own version selector and regulatory profile checkboxes.
The shared validation engine, codification parser, finding model and Excel
report writer are template-agnostic.

## Status overview

| Template | Versions configured | Spec installed | Validation depth |
|----------|---------------------|----------------|------------------|
| TPT      | V7.0 (default)      | ✓ V7.0          | full (~25 cross-field rules + presence + format + codification) |
| TPT      | V6.0                | _missing_       | _planned, see `docs/SPEC_DOWNLOADS.md`_ |
| EET      | V1.1.3, V1.1.2      | _missing_       | _planned (mechanical + DEFERRED for SFDR/Taxonomy/PAI cross-field rules)_ |
| EMT      | V4.3, V4.2          | _missing_       | _planned (mechanical + DEFERRED for MiFID II target market consistency)_ |
| EPT      | V2.1, V2.0          | _missing_       | _planned (mechanical + DEFERRED for PRIIPs scenario logic)_ |

A template whose spec XLSX is not bundled appears in the UI as a placeholder
tab: _"Spec nicht installiert — siehe docs/SPEC_DOWNLOADS.md"_. Drop the
official XLSX into `src/main/resources/spec/<template>/` and add a sibling
`*-info.json` manifest to enable it.

## Screenshots

_Placeholders — to be added once a screenshot pass is run on a system with a display._

- `docs/screenshots/main-tabpane.png` — top-level shell with all four template tabs
- `docs/screenshots/tpt-tab-validate.png` — TPT tab after running validation on a real V7 file
- `docs/screenshots/tpt-tab-export.png` — exported Excel report opened in Excel
- `docs/screenshots/missing-spec-tab.png` — placeholder tab for an uninstalled template

## Supported templates

### TPT — Tripartite Template (Solvency II)

Primary template for insurance-undertaking portfolio reporting.

- **Versions**: V7.0 (2024-11-25, default) — V6.0 _(planned, requires download from findatex.eu)_
- **Regulatory profiles**: Solvency II (M/C/O baseline), IORP / EIOPA / ECB
  (PF.06.02.24 positions/assets, PF.06.03.24 look-through, ECB Addon
  PFE.06.02.30), NW 675, SST (FINMA, opt-in)
- **Validation depth**: 142 datapoints, ~25 cross-field rules (see
  `requirements.md`); ISIN/LEI checksums; optional online cross-check against
  GLEIF & OpenFIGI

### EET — European ESG Template

ESG / SFDR / Taxonomy / PAI data exchange.

_Wird ergänzt — Phase 4.EET. Requires manual download of the V1.1.3 and V1.1.2
spec XLSX files from findatex.eu (login required); see `docs/SPEC_DOWNLOADS.md`._

### EMT — European MiFID Template

MiFID II Target Market & costs.

_Wird ergänzt — Phase 4.EMT. Requires manual download of the V4.3 and V4.2
spec XLSX files from findatex.eu; see `docs/SPEC_DOWNLOADS.md`._

### EPT — European PRIIPs Template

PRIIPs KID inputs.

_Wird ergänzt — Phase 4.EPT. Requires manual download of the V2.1 and V2.0
spec XLSX files from findatex.eu; see `docs/SPEC_DOWNLOADS.md`._

## Layout (multi-module Maven)

| Path | Purpose |
|---|---|
| `pom.xml` | Parent reactor (Java 21; modules: core, javafx-app, web-app) |
| `core/` | UI-agnostic validation, scoring, ingest, report — `com.findatex.validator.{validation,report,ingest,spec,domain,template,external,config}` |
| `core/src/main/resources/spec/` | Bundled template spec XLSX files |
| `core/src/test/resources/sample/` | Canonical test fixtures (also used by web-app tests via relative path) |
| `javafx-app/` | Desktop UI: `App`, `AppLauncher`, `ui/`, `fxml/`, `css/`, `icons/` |
| `web-app/` | Quarkus REST + React SPA |
| `web-app/src/main/java/com/findatex/validator/web/` | api/, service/, filter/, config/, dto/ |
| `web-app/src/main/frontend/` | Vite + React + TS + Tailwind sources |
| `Dockerfile` / `docker-compose.yml` / `.dockerignore` | Web-deployment artefacts |
| `tools/generate_requirements.py` | Re-generates `requirements.md` from the bundled spec xlsx |
| `tools/build_*_samples.py` | Builds the synthetic test samples used by JUnit |
| `package/jpackage.sh` / `.bat` | Native desktop installer build scripts |
| `docs/SPEC_DOWNLOADS.md` | Checklist of spec XLSX files that must be downloaded from findatex.eu |

## Build & run

### Desktop (JavaFX)

```bash
# Run the desktop UI directly:
mvn -pl javafx-app javafx:run

# Or build the shaded fat JAR:
mvn -pl javafx-app -am -DskipTests package
java -jar javafx-app/target/findatex-validator-javafx-1.0.0-shaded.jar

# Build a native installer (Linux .deb/.rpm, macOS app, or Windows .msi):
./package/jpackage.sh        # Linux/macOS
.\package\jpackage.bat       # Windows
```

### Web (Quarkus + React, Docker)

```bash
# Local dev mode (hot reload backend + Vite dev server with /api proxy):
mvn -pl web-app -am quarkus:dev
# in a second terminal:
(cd web-app/src/main/frontend && npm install && npm run dev)
# open http://localhost:5173

# Build the production container:
docker build -t findatex-validator-web:1.0.0 .
docker run --rm -p 8080:8080 findatex-validator-web:1.0.0
# open http://localhost:8080

# Or via compose (reads docker-compose.yml with throttling defaults):
docker compose up -d
```

Anti-misuse defaults (override via env vars; see
`web-app/src/main/resources/application.properties`):

| Env var | Default | Effect |
|---|---|---|
| `FINDATEX_WEB_RATE_LIMIT_PER_IP_PER_HOUR` | `10` | Per-IP token-bucket on `POST /api/validate` |
| `FINDATEX_WEB_MAX_CONCURRENCY`            | `4`  | Global cap on simultaneous validations (overflow → 429) |
| `FINDATEX_WEB_REPORT_TTL_MINUTES`         | `5`  | XLSX report download window |
| `quarkus.http.limits.max-body-size`       | `25M`| Upload size cap (set via `QUARKUS_HTTP_LIMITS_MAX_BODY_SIZE`) |
| `FINDATEX_WEB_EXTERNAL_ENABLED`           | `false` | Enable GLEIF/OpenFIGI cross-checks (TPT only) |

### Tests / regenerators

```bash
mvn test                                       # all 520+ tests across all modules
mvn -Dtest='*ExampleSamplesTest' test          # per-template sample regressions
python3 tools/build_samples.py                 # → core/src/test/resources/sample/*
python3 tools/generate_requirements.py         # → requirements.md from the TPT V7 spec
```

## Validation overview

The validator walks the active template's spec catalog and emits the following
rule families. Cross-field rules are template-specific; only TPT currently has
a deep cross-field rule set (XF-01..XF-25). EET/EMT/EPT will start with
mechanical rules only (presence + format + codification + spec-explicit
conditional presence) and grow as regulatory expertise is added.

- **PRESENCE** — mandatory (`M`) field missing for an active profile (ERROR).
- **COND_PRESENCE** — conditional (`C`) field missing for an active profile
  when the row's applicability scope matches (WARNING).
- **FORMAT/<num>** — wrong codification: numeric, ISO 8601 date, ISO 4217
  currency, ISO 3166-1 alpha-2 country, NACE V2.1, CIC code, alpha/alphanumeric
  length, closed-list values (ERROR).
- **ISIN/<num>/<typeNum>**, **LEI/<num>/<typeNum>** — ID-checksum rules
  attached per template via the rule set (TPT covers instrument/issuer/group/
  underlying/fund-issuer/custodian fields).
- **XF-01..XF-25** — TPT-specific cross-field rules (SCR delivery, position
  weights, NAV consistency, coupon frequency, custodian pair, interest rate
  type, date order, maturity ≥ reporting, PIK patterns, underlying CIC
  mandatoriness, TPT version, conditional presence triggers XF-16..XF-25).
- **LEI-LIVE / ISIN-LIVE** — optional online cross-check against GLEIF and
  OpenFIGI; currently TPT only. Off by default. Works behind corporate
  HTTP/NTLM proxies via *System proxy* mode (recommended) or manual proxy with
  encrypted credentials. See `docs/superpowers/specs/2026-04-27-external-validation-gleif-openfigi-design.md`.

Quality scoring categories (weighted into an overall score 0–100 %):

- Mandatory completeness (40 %)
- Format conformance (20 %)
- Closed-list conformance (15 %)
- Cross-field consistency (15 %)
- Profile completeness average (10 %)

The Excel report (`Export Excel report…` button per tab) emits 5 sheets:
`Summary`, `Scores`, `Findings`, `Field Coverage`, `Per Position`.
