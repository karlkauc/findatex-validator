# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

FinDatEx data-template validator with two distinct UIs sharing one validation
core: a **JavaFX desktop app** (files never leave the user's machine) and a
**Quarkus + React web app** (Docker-deployable, no login, throttled). Four
templates are wired in: **TPT, EET, EMT, EPT** — each with the last two
versions bundled. The public web instance is
<https://www.findatex-validator.eu>. Maven groupId is `com.findatex` and the
package root is `com.findatex.validator` — do **not** rename packages back to
`com.tpt`.

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
mvn -pl javafx-app -am -DskipTests package     # → javafx-app/target/findatex-validator-javafx-<version>-shaded.jar
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
python3 tools/generate_og_image.py             # rebuild the 1200x630 link-preview card
tools/demo/record_desktop_demo.sh              # re-record docs/screenshots/desktop-*.gif (Xvfb + ffmpeg + Pillow; drives the shaded jar)
tools/demo/record_web_demo.sh                  # re-record docs/screenshots/web-app-demo.gif (starts web-app/target/quarkus-app on :18090, Playwright from root package.json)
mvn -pl core -Pdocs exec:java                  # rebuild docs/rules/*.md (per-template rule reference)
./package/jpackage.sh                          # native desktop installer (CDS+AOT+splash baked in; see docs/JPACKAGE_OPTIMIZATIONS.md)
./package/benchmark-startup.sh                 # cold-start bench: none vs CDS vs AOT (5 runs each)
OUT_DIR=… PACKAGE_TYPE=app-image bash package/jpackage.sh   # custom output dir (used by benchmark-startup)
```

Java 21, JavaFX 21, POI 5.x, Commons CSV, Jackson, JUnit 5, AssertJ; web-app
adds Quarkus 3.x (platform BOM version pinned in the root pom), RESTEasy
Reactive, Bucket4j, Caffeine, RestAssured.
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
├── batch/                   BatchValidationService + FolderScanner — validate a whole
│                            folder in one run (desktop-only; driven by TemplateTabController)
├── docs/                    RuleDocGenerator → docs/rules/*.md (run via `mvn -pl core -Pdocs
│                            exec:java`); the output is bundled into both apps and served by
│                            the desktop Help dialog and GET /api/help/rules/{slug}
├── feedback/                GitHubIssueLink (pre-filled false-positive issue URL)
├── stats/                   UsageStatsReporter (opt-out aggregate usage events)
├── newsletter/              NewsletterClient (desktop → web relay, never holds the API key)
├── config/                  AppSettings (json), SettingsService, PasswordCipher (encrypted proxy creds)
└── external/                ExternalValidationService + GLEIF / OpenFIGI clients,
                             cache/, http/, proxy/ (system + manual NTLM)

javafx-app/  (com.findatex.validator)
├── App / AppLauncher        JavaFX entry; calls TemplateRegistry.init()
└── ui/                      MainController (TabPane shell), TemplateTabController
                             (one per template), SettingsController, LookupProgressController,
                             Help/About/Feedback dialogs (MarkdownRenderer), SafeLinkOpener

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
`ExternalValidationService`. Per-version drift is supported via a
per-version `ExternalValidationConfig` (e.g. the custodian LEI columns
140/141 were introduced in TPT V7; V8 reuses V7's identifier layout).

### UI shell (JavaFX)

`MainView.fxml` is a `TabPane` built dynamically from `TemplateRegistry.all()`.
`TemplateTab.fxml` is reused once per template (no template-specific FXML).
Below the score cards a `TabPane` holds **Findings** and **Annotated Source**;
the latter is `AnnotatedSourcePane` (code-built, no FXML) rendering
`core/.../report/AnnotatedSourceModel` — the same cell/finding join the Excel
sheet uses — lazily on first tab open (POI re-read on a background `Task`).
Double-click / context menu on a finding jumps to its cell.
The external-validation controls are shown for any template/version whose
`externalValidationConfigFor(...)` is non-empty (currently all four). Window
title is "FinDatEx Validator" and the macOS dock label uses
`apple.awt.application.name`.

### Web layer (Quarkus + React)

REST endpoints (all under `/api`):
- `GET  /api/templates` — TemplateInfo[] (id, displayName, versions[], profiles[])
- `POST /api/validate`  — multipart (templateId, templateVersion, profiles[], file) → ValidationResponse JSON
- `GET  /api/report/{uuid}` — streams the XLSX once, then evicts the temp file
- `GET  /api/annotated-source/{uuid}` — gzip JSON of the original grid + the
  finding→cell join (`core/.../report/AnnotatedSourceJson`, same uuid as the
  report); repeatable until the report TTL, survives the XLSX download.
  Absent (404, `annotatedSourceAvailable=false` in the validate response) when
  the file exceeds `FINDATEX_WEB_ANNOTATED_SOURCE_MAX_ROWS` / `_MAX_CELLS`
- `GET  /api/feedback-config` — `{githubRepo}` (null when unset); drives the
  "Report a false positive" action
- `POST /api/newsletter/subscribe` — `{email}` → `{status}`; `GET
  /api/newsletter-config` — `{enabled}` (drives whether the SPA shows the form)
- `POST /api/quick-feedback` — `{rating 1..5, comment?, source, appVersion?,
  templateId?}` → `{status}` (star-rating feedback; async insert into the
  usage-stats DB, inert without it); `GET /api/quick-feedback-config` —
  `{enabled}` (drives whether the SPA shows the footer widget)
- `GET  /api/help` + `GET /api/help/rules/{slug}` — bundled help text and the
  generated per-rule Markdown docs (same content as the desktop Help dialog)
- `GET  /api/limits/status` — current rate-limit/quota status for the caller
- `GET  /api/about`, `GET /api/build-info` — About markdown (web-bundle-specific
  third-party list) and Maven version + git metadata of the running container
- `POST /api/usage-stats` — aggregate usage events from desktop installs (see below)
- `POST /api/page-view` — SPA page-load beacon, always 204 (see below)
- `GET  /api/samples/{templateId}` — the demo file behind "Try an example"
  (`SampleFiles`; the version it was generated for is advertised in
  `GET /api/templates`)

Outside `/api` the web layer also serves **server-rendered HTML pages**:
`GET /rules`, `/rules/{slug}` and `/rules/{slug}/field/{num}`
(`RulesPageResource` + `RuleDocs` + `RulesPageRenderer`), `GET /help`
(`HelpPageResource` — the bundled `HELP.md` through the same renderer, plus the
FAQ JSON-LD as a second CSP-hashed inline block; `HelpPageTest`), plus the
generated `GET /sitemap.xml` (`SitemapResource`). These deliberately carry no
React bundle — see the SEO block below.

**SEO / link previews** — four files must agree on one canonical host
(`www.findatex-validator.eu`): the `<link rel="canonical">`, OG and JSON-LD tags
in `web-app/src/main/frontend/index.html`; `robots.txt` and `sitemap.xml` (real
static files under `web-app/src/main/resources/META-INF/resources/`, *not* the
Vite `public/` dir, so they survive `-P backend-only`); and
`findatex.web.canonical-host`, which makes `CanonicalHostFilter` 301 GET/HEAD
from the apex domain and the `*.run.app` URL to the canonical one (empty =
off, the right default for self-hosted instances). `SeoMetadataTest` fails when
they drift. Two traps: the inline JSON-LD is allow-listed in CSP by a
**`sha256-` hash** — editing one character of that block without updating
`script-src` makes browsers drop the structured data silently; and
`SpaFallbackResource` must keep 404-ing file-like paths, otherwise every
unmatched `*.txt`/`*.xml`/`*.js` URL becomes a soft-404 serving the SPA shell.
The link-preview card is generated (`tools/generate_og_image.py`), never
hand-edited. `index.html` carries **metadata only** — the former landing copy
(what the templates are, privacy, scoring, FAQ) lives in `HELP.md`, which is
one source for the desktop Help dialog, the web Help modal and the crawlable
`/help` page; `HelpPageTest` asserts that page names the latest version of
every template and that every FAQ answer in its JSON-LD is visible text. Two
inline JSON-LD blocks, two `sha256-` entries in `script-src`: the
SoftwareApplication node in `index.html` and `HelpPageResource.FAQ_JSON_LD`.

**The rule reference is public** — `/rules`, `/rules/{slug}`,
`/rules/{slug}/field/{num}`. `RuleDocs` splits the generated Markdown on the
generator's own structure (`## 5. Per-field catalog`, then `### Field N — name`),
so a change to `RuleDocGenerator`'s output shape breaks `RuleDocsTest` rather
than silently emptying ~2000 pages. `sitemap.xml` is generated
(`SitemapResource`) — **never re-add a static `sitemap.xml`**, the static
handler wins over the resource. Full picture incl. the manual Search-Console
steps: `docs/SEO.md`.

**Misbrauch-Schutz** (configurable via `FINDATEX_WEB_*` env vars; defaults in
`web-app/src/main/resources/application.properties`):
1. **Per-IP rate limit** (Bucket4j, default 30/h) — only on `POST /api/validate`.
2. **Concurrency cap** (`Semaphore`, default 4 in flight) — overflow → HTTP 429.
3. **Body size limit** (`quarkus.http.limits.max-body-size=25M`) → HTTP 413.
4. **Auto-delete uploads + reports** (Quarkus deletes upload tempfiles on
   request end; `ReportStore` evicts XLSX after first download or 5-min TTL.
   The annotated-source JSON sits in a sibling cache under the same uuid:
   readable repeatedly, deleted at TTL — the XLSX `take()` never touches it).

External validation (GLEIF/OpenFIGI) is **off by default in the web layer**.
Operators flip it on via `FINDATEX_WEB_EXTERNAL_ENABLED=true` and provide
keys/proxy creds via env. The public Cloud Run instance has it **on** (the
deploy workflow sets the flag and mounts `findatex-openfigi-key` from Secret
Manager — a manual prerequisite, see `docs/DEPLOY_CLOUDRUN.md`); the per-run
checkbox still defaults to off. With the operator switch on, `TemplateResource`
surfaces `externalAvailable=true` for every template that declares an
`ExternalValidationConfig` (currently all four), and `ValidationOrchestrator`
runs the GLEIF/OpenFIGI pipeline through that config when the per-request
`externalEnabled=true` flag is set.

**Report a false positive** — both UIs let the user open a *pre-filled* GitHub
issue for a wrong finding. No token, no SMTP, no server-side issue creation:
the shared builder `core/.../feedback/GitHubIssueLink` (TS mirror at
`web-app/.../frontend/src/feedback/githubIssue.ts`) produces the issue URL; the
user reviews the exact body in a confirm dialog/modal and submits it on GitHub
themselves. Target repo is configurable and **empty by default** (action hidden
when unset): desktop via Settings → Feedback (`AppSettings.Feedback.githubRepo`,
persisted in `settings.json`); web via `FINDATEX_WEB_FEEDBACK_GITHUB_REPO`
surfaced through `GET /api/feedback-config`. The desktop opens the URL via the
existing `SafeLinkOpener` (scheme-allowlisted `Desktop.browse`).

**Quick feedback (star rating)** — low-barrier 1–5-star rating + optional
comment on both UIs. Naming split: `feedback` (package/endpoints) = the
false-positive GitHub-issue feature above; `quickfeedback` = this star rating —
don't mix them. Desktop (`core/.../quickfeedback/QuickFeedbackClient`, header
button "★ Rate this app") POSTs to the web endpoint configured in Settings →
Feedback (default `https://www.findatex-validator.eu`, blank = disabled);
web posts from the footer widget (`QuickFeedback.tsx`). Storage:
`quick_feedback` table in the usage-stats DB (`QuickFeedbackService`, async +
retry, **inert with no `FINDATEX_WEB_USAGE_DB_URL`**); no install id, no IP.
Rate-limited via `FINDATEX_WEB_QUICK_FEEDBACK_RATE` (default 5/h per IP).
Details in `docs/QUICK_FEEDBACK.md`. The GitHub repo link shown in both UI
headers comes from `AppInfo.githubUrl()` — the single source for the repo URL.

**Usage statistics (opt-out)** — aggregate-only usage events, default on, per
install. One table, `usage_event`, told apart by `event_type` (`validate` incl.
failed runs with a `status` class, `report_download`, `sample_load`,
`page_view`). Desktop never writes the DB: `core/.../stats/UsageStatsReporter`
fire-and-forgets a `UsageEvent` (aggregate counts and *classes* only — never
files, names, codes, cell/finding content, or IP) to `POST /api/usage-stats`
(`X-Usage-Token`); failures are silently dropped (DEBUG), the run is never
disturbed. Old desktop builds still post the pre-2026-09 JSON — every new DTO
field is nullable and defaults to `validate`/`ok`. Web runs self-record from
`ValidationOrchestrator` (sentinel install id); downloads/samples/failures from
`ReportResource`, `SampleResource`, `ValidationResource`, `RateLimitFilter`.
The web layer is the sole DB writer via plain Agroal/JDBC (`UsageStatsService`,
row shape `UsageRow`) — **inert with no `FINDATEX_WEB_USAGE_DB_URL`** (app/tests
still boot). Everything request-derived comes from one place,
`ClientContextFactory` → `ClientContext`: `country_code` (`GeoIpService`,
offline GeoLite2), the daily-rotating `visitor_hash` (`VisitorHasher`,
`sha256(HMAC(FINDATEX_WEB_VISITOR_SALT_SECRET, day) | ip | ua)`, same scheme as
the viewer apps), `device`/`os_name` from the UA (`UserAgentClassifier` — a web
run must never record the *server's* `os.name`). The **raw IP is never stored
or logged**. File attributes are reduced by `core/.../stats/FileNameShape` to
format / size / naming-pattern class — the only code allowed to look at a file
name for stats; the external phase is counted by `ExternalLookupCounter`
(a `ProgressSink` decorator). Opt-out: desktop Settings → Statistik
(`AppSettings.UsageStats`); the install id is generated and persisted by
`SettingsService`. Full schema, migration, env vars and psql ops in
`docs/USAGE_STATS.md`. Never add instance data (names, paths, codes, cell or
finding content, IP) to `UsageEvent`; derived, non-identifying classes are
fine but must go through `FileNameShape`. HTTP 413 (upload over
`max-body-size`) is **not** counted — Quarkus rejects it before any router.

**Page views** — runs alone cannot say whether a quiet week means nobody came
or everybody left without uploading. The SPA fires one beacon per page load
from `main.tsx` (`POST /api/page-view`, **always 204**); `PageViewService`
sanitises it and `UsageStatsService` stores it as `event_type='page_view'`
(the old `page_view` table is legacy, backfilled once, read-only). Client-side
on purpose: server-side counting would count crawlers, which at this traffic
level would dominate. No cookie, no full referrer URL, no query strings — only
path, referrer **host**, campaign slug (`?utm_source=`/`?ref=`), country and the
daily visitor hash; bot UAs are dropped by `BotDetector`. The funnel
(page_view → validate → report_download per visitor) lives in the usage
dashboard (`~/webdav/usage_statistics`, findatex profile) and in
`tools/usage_report.py` under **Traffic**.

**Newsletter sign-up (external provider)** — user-initiated, so **synchronous
with a clear result** (not fire-and-forget). The e-mail is **never stored in
our DB or logs**: it is forwarded to an external provider (MailerLite default;
`NewsletterProvider` seam, EmailOctopus documented) which owns double-opt-in,
unsubscribe and deletion. Desktop never holds the API key — `core/.../newsletter/
NewsletterClient` (proxy/NTLM-aware, shares `NewsletterStatus`/`EmailAddress`
with the web layer) POSTs to `POST /api/newsletter/subscribe`; the web
`NewsletterService`→provider is the only API-key holder. **Inert with no
`FINDATEX_WEB_NEWSLETTER_API_KEY`** (POST → 503, SPA hides the form via
`GET /api/newsletter-config`). Strict per-IP rate limit (anti e-mail-bombing).
Desktop endpoint URL: Settings → Newsletter (`AppSettings.Newsletter`). Full
setup, GDPR/DPA notes and the EmailOctopus variant in `docs/NEWSLETTER.md`.

**Web UI shell (React)** — `App.tsx` renders `SidebarLayout`: a 320 px input
column (Input card + Notes) that collapses into a slim rail, results taking the
rest of the viewport (no max-width). Scores, Per Fund and Notes are
`CollapsibleSection`s; every open/closed state persists via
`lib/usePersistedState.ts` (`findatex.ui.*` in localStorage, try/catch —
never a hard dependency). `ResultPanel` (keyed by `reportId`) holds a
`TabStrip` *Findings* | *Annotated Source*; `AnnotatedSourceView` fetches
`/api/annotated-source/{id}` lazily on first activation and renders the grid
without virtualisation (defaults: only rows with findings, 200-row pages),
`lib/annotatedSource.ts` mirrors core's `describe()` / column letters.
`FindingsTable`'s `onShowInSource(findingIndex)` (Source button / row
double-click) jumps to the cell — the index refers to `findings` as delivered,
which is why the response's finding order must stay the report's order.

The React frontend lives in `web-app/src/main/frontend/`. Vite writes the
production bundle into `web-app/target/classes/META-INF/resources/`, which
Quarkus serves as the SPA root. `frontend-maven-plugin` runs `npm install`
and `npm run build` during Maven's `generate-resources` phase. Dev mode:
`mvn -pl web-app -am quarkus:dev` plus `npm run dev` in the frontend dir
(Vite proxies `/api` to `:8080`).

## Test fixtures

- `core/src/test/resources/sample/` — 3 minimal canonical fixtures used by core unit tests
  (also referenced by web-app tests via the relative path `../core/src/test/resources/sample/`).
- `samples/<template>/` — generator-driven fixtures consumed by `*ExampleSamplesTest`.
  Two kinds: `00_showcase.xlsx` is the file behind "Try an example" (a full
  delivery — 60 TPT positions over three funds, 25 share classes for the other
  templates — with a curated spread of defects; built from `tools/tpt_showcase.py`
  and `tools/findatex_realistic_values.py`), while `01_…` upwards are small
  one-rule-family regression fixtures. Regenerate with the matching
  `tools/build_*_samples.py` whenever the spec or the rule set changes.
  **A new file under `samples/` reaches the container only if you widen all
  three of** `web-app/pom.xml`'s `<resources>` include, the `.dockerignore`
  negation, and the `SampleFiles` map — each fails silently on its own
  (`Application` logs a startup warning as the safety net).
- `specs/` is the operator's drop zone (verbatim FinDatEx downloads, German
  filenames, .DS_Store noise). Files get **copied** into
  `core/src/main/resources/spec/<t>/` with normalised names — never reference
  `specs/` paths from production code.

## Local-only dev tooling (gitignored, may be absent in other clones)

- `dirtyTests/` + `validate_dirty_tests.py` — real-world vendor files (UBS,
  Amundi, WisdomTree …) smoke-tested against a running container
  (`docker compose up -d`, API on :18082); summary lands in
  `dirtyTests_results.json`. These are **real fund files — never commit them**.
- `scraper/` — Playwright-based vendor-file scraper with its own
  `node_modules/` and `runs/` output; the root `package.json` only pins
  Playwright for it.

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

## Native packaging (jpackage)

`package/jpackage.sh` runs a two-stage flow: (1) build intermediate
app-image, (2) training run that auto-exits via App.java's
`-Dfindatex.training=<ms>` hook to dump a CDS archive (`app.jsa`) or AOT
cache (`app.aot`), (3) patch the launcher `.cfg` and wrap into the final
installer. Defaults: low `-Xms`, `-XX:TieredStopAtLevel=1`, splash baked
in, AOT on JDK ≥ 24, dynamic CDS otherwise. Full rationale + gotchas
in `docs/JPACKAGE_OPTIMIZATIONS.md`.

- **Runtime JVM options live in `<APP_NAME>.cfg`** — `-J<flag>` on the
  launcher CLI is jpackage build-time-only, silently swallowed at runtime.
  `_JAVA_OPTIONS` works but splits on whitespace, mangling any path that
  contains "FinDatEx Validator". Patch the `.cfg` for path-bearing flags.
- **AOT/CDS path-sensitivity**: archive encodes the classpath path. Works
  for `app-image` (portable, build-path == install-path); breaks for
  installer types after install (warning + fallback to vanilla CDS).
- **AOT training needs the bundled JVM**, not system `java` — runtime's
  `lib/modules` hash must match. jpackage strips `bin/java`; the script
  copies `$JAVA_HOME/bin/java` in for training and removes it after.
- **Preserve timestamps when relocating the bundle** — AOT verifies jar
  mtime, so `cp -a` on Linux/macOS, `robocopy /COPYALL` on Windows.
- **Headless training (xvfb) requires `System.exit()` fallback** — under
  xvfb the GTK runloop sometimes won't process `Platform.exit()`. App.java's
  `maybeScheduleTrainingExit` schedules a hard exit 1.5 s after Platform.exit.
