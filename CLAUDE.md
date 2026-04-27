# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

JavaFX desktop app that loads a [FinDatEx](https://findatex.eu) data template
(`.xlsx` / `.csv`) and produces a quality / conformance report against the
relevant template specification. Four templates are wired in: **TPT, EET, EMT,
EPT** — each with the last two versions bundled. Maven groupId is
`com.findatex` and the package root is `com.findatex.validator` (the directory
name `tpt_test` is historical — do **not** rename packages back to `com.tpt`).

## Build & run

```bash
mvn javafx:run                   # run the JavaFX UI
mvn -DskipTests package          # build fat jar → target/tpt-validator-1.0.0-shaded.jar
mvn test                         # run all tests (~460+ JUnit 5)
mvn -Dtest=ClassName test        # single test class
mvn -Dtest=ClassName#method test # single test method
mvn -Dtest='*ExampleSamplesTest' test   # all per-template sample regressions
mvn clean verify                 # full regression + JaCoCo report
xvfb-run mvn javafx:run          # headless smoke test (no DISPLAY)

python3 tools/build_samples.py            # regenerate src/test/resources/sample/*
python3 tools/build_examples.py           # regenerate samples/tpt/*
python3 tools/build_eet_samples.py        # samples/eet/* (also _emt_, _ept_)
python3 tools/generate_requirements.py    # rebuild requirements.md from spec
./package/jpackage.sh                     # native installer (Linux .deb/.rpm, macOS .app)
```

Java 21, JavaFX 21, POI 5.x, Commons CSV, Jackson, JUnit 5, AssertJ. Surefire
passes `--enable-native-access=ALL-UNNAMED` (POI on Java 21).

## Architecture (template-agnostic core + per-template plugins)

The codebase was extended from TPT-only to multi-template via the plan in
`RALPH_PROMPT.md`; status lives in `RALPH_STATUS.md`. The shape now is:

```
com.findatex.validator
├── App / AppLauncher        JavaFX entry; calls TemplateRegistry.init()
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
├── ingest/                  TptFileLoader (xlsx/csv dispatch), HeaderMapper
├── domain/                  TptFile, TptRow, RawCell, CicCode
├── validation/              ValidationEngine, Rule, Finding, Severity,
│                            FindingEnricher, refdata/, rules/ (presence,
│                            format, ISIN, LEI, conditional + crossfield/*)
├── report/                  QualityScorer, QualityReport, XlsxReportWriter (5 sheets)
├── ui/                      MainController (TabPane shell), TemplateTabController
│                            (one per template), SettingsController, LookupProgressController
├── config/                  AppSettings (json), SettingsService, PasswordCipher (encrypted proxy creds)
└── external/                ExternalValidationService + GLEIF / OpenFIGI clients,
                             cache/, http/, proxy/ (system + manual NTLM)
```

The flow is always: `TemplateRegistry.of(id)` → `specLoaderFor(version).load()` →
`TptFileLoader(catalog).load(path)` → `ValidationEngine(catalog, ruleSet).validate(file, profiles)`
→ `FindingEnricher.enrich` → `QualityScorer` / `XlsxReportWriter`.

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
`// DEFERRED: requires regulatory SME — <which regulation, which fields>` and
listed under `DEFERRED:` in `RALPH_STATUS.md`. **Never invent regulatory
logic** for a non-TPT template.

`ConditionalRequirement` + `ConditionalFieldPresenceRule` is the generic
"if field X = Y then Z is mandatory" mechanism — prefer it over a new
crossfield class when the spec text is reducible to that shape.

### External validation (TPT only, opt-in)

`ExternalValidationService` cross-checks ISIN against OpenFIGI and LEI against
GLEIF. Off by default; configured per session via the Settings dialog. Works
behind corporate NTLM proxies in *System proxy* mode (`ProxyService`) or with
encrypted manual creds (`PasswordCipher`). See
`docs/superpowers/specs/2026-04-27-external-validation-gleif-openfigi-design.md`
if extending.

### UI shell

`MainView.fxml` is a `TabPane` built dynamically from `TemplateRegistry.all()`.
`TemplateTab.fxml` is reused once per template (no template-specific FXML).
The external-validation controls are hidden for non-TPT tabs. Window title
is "FinDatEx Validator" and the macOS dock label uses `apple.awt.application.name`.

## Test fixtures

- `src/test/resources/sample/` — 3 minimal canonical fixtures used by core unit tests.
- `samples/<template>/` — generator-driven scenario fixtures (clean, missing-mandatory,
  bad-formats, …) consumed by `*ExampleSamplesTest`. Regenerate with the matching
  `tools/build_*_samples.py` whenever the spec or the rule set changes.
- `specs/` is the operator's drop zone (verbatim FinDatEx downloads, German
  filenames, .DS_Store noise). Files get **copied** into
  `src/main/resources/spec/<t>/` with normalised names — never reference
  `specs/` paths from production code.

## Spec acquisition

FinDatEx spec XLSXs are login-walled (no stable URLs). When a spec is missing,
`docs/SPEC_DOWNLOADS.md` is the canonical checklist. `docs/SPEC_INVENTORY.md`
mirrors what's physically present in `src/main/resources/spec/`.

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

- Run `mvn test` after every change. The Ralph loop's contract is "never
  break green tests"; honour the same baseline (currently 460+ tests).
- Keep the manifest-driven path for new template versions — config-only
  additions are the goal.
- Don't modify files under `specs/` (operator drop zone). Don't commit real
  fund instance files (gitignore: `20260331_TPTV7_*.xlsx`, `.DAV/`).
