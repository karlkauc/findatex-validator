# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

FinDatEx data-template validator with two distinct UIs sharing one validation
core: a **JavaFX desktop app** (files never leave the user's machine) and a
**Quarkus + React web app** (Docker-deployable, no login, throttled). Four
templates are wired in: **TPT, EET, EMT, EPT** — each with the last two
versions bundled. Maven groupId is `com.findatex` and the package root is
`com.findatex.validator` — do **not** rename packages back to `com.tpt`.

## Repo layout (multi-module Maven)

```
findatex-validator-parent/  (root pom, packaging=pom)
├── core/        UI-agnostic validation, scoring, ingest, report (~510 tests)
├── javafx-app/  Desktop UI (App, AppLauncher, ui/, fxml/, css/, icons/)
└── web-app/     Quarkus REST + React SPA, Dockerfile target
```

`core/` is what `javafx-app/` and `web-app/` both depend on. Never let
JavaFX or Jakarta-EE imports leak into `core/` — that's the whole point of
the split.

## Build & run

```bash
# --- root reactor (covers all modules) ---------------------------------------
mvn test                                       # all tests (~520+ JUnit 5)
mvn -DskipTests package                        # builds shaded JavaFX jar + Quarkus fast-jar
mvn -Dtest=ClassName test                      # single test class (any module)
mvn -Dtest='*ExampleSamplesTest' test          # all per-template sample regressions
mvn clean verify                               # full regression + JaCoCo report

# --- JavaFX desktop ----------------------------------------------------------
mvn -pl javafx-app javafx:run                  # run the desktop UI
mvn -pl javafx-app -am -DskipTests package     # → javafx-app/target/findatex-validator-javafx-1.0.0-shaded.jar
xvfb-run mvn -pl javafx-app javafx:run         # headless smoke test (no DISPLAY)

# --- Web (Quarkus + React) ---------------------------------------------------
mvn -pl web-app -am quarkus:dev                # backend dev mode, picks up code changes
(cd web-app/src/main/frontend && npm run dev)  # vite dev server on :5173 (proxies /api → :8080)
mvn -pl web-app -am -DskipTests package        # → web-app/target/quarkus-app/
mvn -pl web-app -am -P backend-only -DskipTests package   # backend without frontend rebuild
docker build -t findatex-validator-web:1.0.0 . # container build (multi-stage)
docker compose up -d                           # run the container with the bundled defaults

# --- generators / packaging --------------------------------------------------
python3 tools/build_samples.py                 # regenerate core/src/test/resources/sample/*
python3 tools/build_examples.py                # regenerate samples/tpt/*
python3 tools/build_eet_samples.py             # samples/eet/*  (also _emt_, _ept_)
python3 tools/generate_requirements.py         # rebuild requirements.md from spec
mvn -pl core -Pdocs exec:java                  # rebuild docs/rules/*.md (per-template rule reference)
./package/jpackage.sh                          # native desktop installer
```

Java 21, JavaFX 21, POI 5.x, Commons CSV, Jackson, JUnit 5, AssertJ; web-app
adds Quarkus 3.17.x, RESTEasy Reactive, Bucket4j, Caffeine, RestAssured.
Surefire passes `--enable-native-access=ALL-UNNAMED` (POI on Java 21).

## Architecture (template-agnostic core + per-template plugins)

The codebase was extended from TPT-only to multi-template; the shape now is:

```
core/  (com.findatex.validator)
├── template/api/            template-agnostic abstractions (read this first)
│   ├── TemplateId           enum: TPT, EET, EMT, EPT
│   ├── TemplateVersion      record (id, version string, label, xlsx path,
│   │                         sheet name, release date, manifest path)
│   ├── TemplateDefinition   versions(), profiles(), specLoaderFor(v),
│   │                         ruleSetFor(v), profilesFor(v) (EPT overrides)
│   ├── TemplateRegistry     process-wide directory; init() registers all 4
│   ├── TemplateSpecLoader   load() → SpecCatalog
│   ├── TemplateRuleSet      build(catalog, profiles) → List<Rule>
│   └── ProfileKey/ProfileSet  per-template profile dimension
├── template/{tpt,eet,emt,ept}/   per-template TemplateDefinition + Profiles + RuleSet
├── spec/                    SpecCatalog, FieldSpec, Flag (M/C/O/I/N/A),
│                            CodificationParser, ApplicabilityScope (sealed:
│                            CicApplicabilityScope for TPT, EmptyApplicabilityScope otherwise),
│                            SpecManifest (Jackson record), ManifestDrivenSpecLoader
├── ingest/                  TptFileLoader (xlsx/csv dispatch — load(Path) AND load(InputStream, filename)),
│                            HeaderMapper, XlsxLoader, CsvLoader
├── domain/                  TptFile, TptRow, RawCell, CicCode
├── validation/              ValidationEngine, Rule, Finding, Severity,
│                            FindingEnricher, refdata/, rules/ (presence,
│                            format, ISIN, LEI, conditional + crossfield/*)
├── report/                  QualityScorer, QualityReport, XlsxReportWriter (5 sheets)
├── config/                  AppSettings (json), SettingsService, PasswordCipher (encrypted proxy creds)
└── external/                ExternalValidationService + GLEIF / OpenFIGI clients,
                             cache/, http/, proxy/ (system + manual NTLM)

javafx-app/  (com.findatex.validator)
├── App / AppLauncher        JavaFX entry; calls TemplateRegistry.init()
└── ui/                      MainController (TabPane shell), TemplateTabController
                             (one per template), SettingsController, LookupProgressController

web-app/  (com.findatex.validator.web)
├── Application              @Startup hook → TemplateRegistry.init()
├── api/                     TemplateResource, ValidationResource, ReportResource,
│                            SpaFallbackResource (serves the React index.html for
│                            non-/api routes)
├── service/                 ValidationOrchestrator (Semaphore-gated, holds the
│                            cached SpecCatalog/RuleSet bundle per template+version),
│                            ReportStore (Caffeine cache, TTL → file delete)
├── filter/                  RateLimitFilter (Bucket4j, per X-Forwarded-For IP)
├── config/                  WebConfig (@ConfigProperty fields, all ENV-overridable)
└── dto/                     TemplateInfo, ValidationResponse, FindingDto, ScoreDto
└── src/main/frontend/       React + Vite + TypeScript SPA (Tailwind, react-query,
                             react-dropzone). Vite output: target/classes/META-INF/
                             resources/  → Quarkus serves it as the document root.
```

The validation flow is always:
`TemplateRegistry.of(id)` → `specLoaderFor(version).load()` →
`TptFileLoader(catalog).load(...)` → `ValidationEngine(catalog, ruleSet).validate(file, profiles)`
→ `FindingEnricher.enrich` → `QualityScorer` / `XlsxReportWriter`.

Both UIs invoke this exact same flow. The only delta is the loader entry
point: JavaFX uses `load(Path)` (FileChooser); web uses `load(InputStream,
filename)` (Multipart upload — no tempfile written through).

### Adding a template version (manifest-driven)

1. Drop XLSX into `src/main/resources/spec/<template>/`.
2. Author sibling `*-info.json` (`SpecManifest` record — see
   `tpt-v7-info.json`): sheet name, `firstDataRow`, 1-based column indices,
   `applicabilityColumns` (`kind: "CIC"` or `"none"`), `profileColumns` with
   `kind: "flag"` or `"presenceMerge"`.
3. Add a `TemplateVersion` constant in the per-template `*Template.java` and
   include it in `versions()`.
4. `mvn test` — `TemplateRegistryTest` and the per-template `*SpecLoaderTest` /
   `*RuleSetTest` will pick it up. The UI's `MainController` probes
   `specLoaderFor(latest()).load()` per template and silently downgrades a
   template to a "Spec nicht installiert" placeholder tab if loading throws.

`SpecLoader` is the legacy hand-written TPT V7 loader; **do not extend it for
new templates** — go through `ManifestDrivenSpecLoader`. It still exists for
the equivalence regression in `SpecLoaderTest`.

### Rules

Cross-field rules live in `validation/rules/crossfield/`. TPT's rule set is
deep (~25 rules: SCR delivery, weight sums, NAV, coupon frequency, custodian
pair, interest rate type, date order, maturity, PIK, underlying CIC, version,
plus XF-16..XF-25 conditional triggers). EET / EMT / EPT rule sets are
intentionally **mechanical only** (presence + format + codification +
spec-explicit conditional presence). Anything regulatory (SFDR, MiFID II
target market, PRIIPs RTS scenarios) is marked
`// DEFERRED: requires regulatory SME — <which regulation, which fields>`.
**Never invent regulatory logic** for a non-TPT template.

`ConditionalRequirement` + `ConditionalFieldPresenceRule` is the generic
"if field X = Y then Z is mandatory" mechanism — prefer it over a new
crossfield class when the spec text is reducible to that shape.

### External validation (opt-in, all templates)

`ExternalValidationService` cross-checks ISIN against OpenFIGI and LEI against
GLEIF. Off by default; configured per session via the Settings dialog. Works
behind corporate NTLM proxies in *System proxy* mode (`ProxyService`) or with
encrypted manual creds (`PasswordCipher`).

The service itself is template-agnostic — each `TemplateDefinition` declares its
ISIN/LEI columns via `externalValidationConfigFor(version)` returning an
`ExternalValidationConfig` (per-template constants live in `TptTemplate`,
`EetTemplate`, `EmtTemplate`, `EptTemplate`). To extend: drop new column
references into the constant; never add hardcoded field codes back into
`ExternalValidationService`. Per-version drift is supported (TPT V6 omits the
custodian LEI columns introduced in V7).

### UI shell (JavaFX)

`MainView.fxml` is a `TabPane` built dynamically from `TemplateRegistry.all()`.
`TemplateTab.fxml` is reused once per template (no template-specific FXML).
The external-validation controls are shown for any template/version whose
`externalValidationConfigFor(...)` is non-empty (currently all four). Window
title is "FinDatEx Validator" and the macOS dock label uses
`apple.awt.application.name`.

### Web layer (Quarkus + React)

REST endpoints (all under `/api`):
- `GET  /api/templates` — TemplateInfo[] (id, displayName, versions[], profiles[])
- `POST /api/validate`  — multipart (templateId, templateVersion, profiles[], file) → ValidationResponse JSON
- `GET  /api/report/{uuid}` — streams the XLSX once, then evicts the temp file

**Misbrauch-Schutz** (configurable via `FINDATEX_WEB_*` env vars; defaults in
`web-app/src/main/resources/application.properties`):
1. **Per-IP rate limit** (Bucket4j, default 10/h) — only on `POST /api/validate`.
2. **Concurrency cap** (`Semaphore`, default 4 in flight) — overflow → HTTP 429.
3. **Body size limit** (`quarkus.http.limits.max-body-size=25M`) → HTTP 413.
4. **Auto-delete uploads + reports** (Quarkus deletes upload tempfiles on
   request end; `ReportStore` evicts XLSX after first download or 5-min TTL).

External validation (GLEIF/OpenFIGI) is **off by default in the web layer**.
Operators flip it on via `FINDATEX_WEB_EXTERNAL_ENABLED=true` and provide
keys/proxy creds via env. With the operator switch on, `TemplateResource`
surfaces `externalAvailable=true` for every template that declares an
`ExternalValidationConfig` (currently all four), and `ValidationOrchestrator`
runs the GLEIF/OpenFIGI pipeline through that config when the per-request
`externalEnabled=true` flag is set.

The React frontend lives in `web-app/src/main/frontend/`. Vite writes the
production bundle into `web-app/target/classes/META-INF/resources/`, which
Quarkus serves as the SPA root. `frontend-maven-plugin` runs `npm install`
and `npm run build` during Maven's `generate-resources` phase. Dev mode:
`mvn -pl web-app -am quarkus:dev` plus `npm run dev` in the frontend dir
(Vite proxies `/api` to `:8080`).

## Test fixtures

- `core/src/test/resources/sample/` — 3 minimal canonical fixtures used by core unit tests
  (also referenced by web-app tests via the relative path `../core/src/test/resources/sample/`).
- `samples/<template>/` — generator-driven scenario fixtures (clean, missing-mandatory,
  bad-formats, …) consumed by `*ExampleSamplesTest`. Regenerate with the matching
  `tools/build_*_samples.py` whenever the spec or the rule set changes.
- `specs/` is the operator's drop zone (verbatim FinDatEx downloads, German
  filenames, .DS_Store noise). Files get **copied** into
  `core/src/main/resources/spec/<t>/` with normalised names — never reference
  `specs/` paths from production code.

## Spec acquisition

FinDatEx spec XLSXs are login-walled (no stable URLs). When a spec is missing,
`docs/SPEC_DOWNLOADS.md` is the canonical checklist. `docs/SPEC_INVENTORY.md`
mirrors what's physically present in `core/src/main/resources/spec/`.

## Conventions

- All template-aware code goes through `ProfileKey` (string code + display).
  The legacy `Profile` enum has been deleted — do not reintroduce it.
- Display strings shown in the UI / report come from `ProfileKey.displayName()`
  and are byte-identical to the historical TPT enum labels — preserve them.
- Findings carry `templateVersion`, `profile`, and (after `FindingEnricher`)
  position context (fund name, ISIN, valuation date, weight). The Excel
  report's 5 sheets (`Summary`, `Scores`, `Findings`, `Field Coverage`,
  `Per Position`) are profile-aware.
- Quality scoring weights (must sum to 100): mandatory completeness 40,
  format 20, closed-list 15, cross-field 15, profile-completeness avg 10.
  Tweak in `QualityScorer` if changing — `QualityScorerEdgeCasesTest` will
  catch silent regressions.

## When extending

- Run `mvn test` after every change. Never break green tests; honour the
  current baseline (520+ tests across `core` + `javafx-app` + `web-app`).
- Keep the manifest-driven path for new template versions — config-only
  additions are the goal.
- Don't put JavaFX or Jakarta-EE imports into `core/`. The split exists so
  the web container doesn't drag in a 50MB JavaFX runtime, and so the
  desktop user gets no Quarkus heap overhead.
- Don't modify files under `specs/` (operator drop zone). Don't commit real
  fund instance files (gitignore: `20260331_TPTV7_*.xlsx`, `.DAV/`).
