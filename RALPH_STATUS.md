# Ralph Loop Status — FinDatEx Multi-Template Extension

**Baseline:** 450 tests, all green (recorded 2026-04-27 from sub-step 0.1).

## Phase 0 — Foundation refactor [x] DONE
- [x] 0.1 Baseline test count recorded (450 tests).
- [x] 0.2 Empty abstractions in `com.tpt.validator.template.api`.
- [x] 0.3 `TptProfiles` with ProfileKey constants.
- [x] 0.4 `TptTemplate` + register in `TemplateRegistry` + `TemplateRegistryTest`.
- [x] 0.5 `FieldSpec.flags` → `Map<String, Flag>` keyed by code, `flag(Profile)` accessor preserved.
- [x] 0.6 `ApplicabilityScope` sealed interface + `CicApplicabilityScope` + `EmptyApplicabilityScope`.
- [x] 0.7 `TptRuleSet` + `RuleRegistry` becomes facade.
- [x] 0.8 `App.start()` consumes catalog + rules through `TemplateRegistry`.
- [x] 0.9 Migrate all `Profile` references to `ProfileKey`/`TptProfiles`. Delete `Profile.java`.
- [x] 0.10 Final regression: `mvn clean verify` green (455 tests = 450 baseline + 5 TemplateRegistryTest); both JARs built. `mvn javafx:run` not directly verified — headless environment (no DISPLAY); UI code path covered by `TemplateRegistryTest`.

## Phase 1 — TabPane UI shell [x] DONE
- [x] 1.1 `TemplateTabController(TemplateDefinition)`.
- [x] 1.2 `TemplateTab.fxml` with version dropdown, file picker, profile checkboxes, validate/export, results table.
- [x] 1.3 `MainView.fxml` body becomes `TabPane` from `TemplateRegistry.all()`.
- [x] 1.4 `MainController` becomes shell builder.
- [x] 1.5 Window title → "FinDatEx Validator"; macOS dock + pom.xml updates.
- [x] 1.6 Tabs without spec show "Spec nicht installiert" message.
- [x] 1.7 `mvn javafx:run` verification (background, kill).
- [x] 1.8 README skeleton with one section per template.

## Phase 2 — Manifest-driven SpecLoader [x] DONE
- [x] 2.1 `SpecManifest` Jackson record + `tpt-v7-info.json`.
- [x] 2.2 Rename TPT V7 XLSX (no double spaces) and update references.
- [x] 2.3 `ManifestDrivenSpecLoader` byte-identical to current `SpecLoader.loadBundled()`.
- [x] 2.4 `ManifestDrivenSpecLoaderTest` with synthetic manifest + synthetic XLSX.
- [x] 2.5 Existing `SpecLoaderTest` migrates to manifest path.

## Phase 3 — TPT V6 [!] BLOCKED-DOWNLOAD
- [!] 3.1 SPEC ACQUISITION — TPT V6 XLSX not present at `src/main/resources/spec/tpt/TPT_V6_*.xlsx`; needs human download from findatex.eu (login required). Sub-steps 3.2–3.5 cannot proceed without this file.
- [ ] 3.2 `tpt-v6-info.json` from inspecting actual sheet header.
- [ ] 3.3 V6 added to `TptTemplate.versions()`.
- [ ] 3.4 `TptVersionRule` parametrized.
- [ ] 3.5 V7 sample file under V6 yields version-mismatch finding.

## Phase 4.EET [!] BLOCKED-DOWNLOAD
- [!] 4.EET.1 SPEC ACQUISITION — `src/main/resources/spec/eet/` does not exist; no EET XLSX anywhere in the repo. Needs human download of EET V1.1.3 and V1.1.2 from findatex.eu (login required). Sub-steps 4.EET.2–8 cannot proceed.
- [ ] 4.EET.2 `EetProfiles`.
- [ ] 4.EET.3 Manifest JSONs.
- [ ] 4.EET.4 `EetTemplate` registered.
- [ ] 4.EET.5 `EetRuleSet` (mechanical only; deeper rules DEFERRED).
- [ ] 4.EET.6 Test fixtures (clean + malformed).
- [ ] 4.EET.7 `EetSpecLoaderTest` + `EetRuleSetTest`.
- [ ] 4.EET.8 `mvn clean verify` + `mvn javafx:run` shows EET tab.

## Phase 4.EMT [!] BLOCKED-DOWNLOAD
- [!] 4.EMT.1 SPEC ACQUISITION — `src/main/resources/spec/emt/` does not exist; no EMT XLSX anywhere in the repo. Needs human download of EMT V4.3 and V4.2 from findatex.eu (login required). Sub-steps 4.EMT.2–8 cannot proceed.
- [ ] 4.EMT.2 `EmtProfiles`.
- [ ] 4.EMT.3 Manifest JSONs.
- [ ] 4.EMT.4 `EmtTemplate` registered.
- [ ] 4.EMT.5 `EmtRuleSet` (mechanical only).
- [ ] 4.EMT.6 Test fixtures.
- [ ] 4.EMT.7 `EmtSpecLoaderTest` + `EmtRuleSetTest`.
- [ ] 4.EMT.8 `mvn clean verify` + `mvn javafx:run` shows EMT tab.

## Phase 4.EPT [!] BLOCKED-DOWNLOAD
- [!] 4.EPT.1 SPEC ACQUISITION — `src/main/resources/spec/ept/` does not exist; no EPT XLSX anywhere in the repo. Needs human download of EPT V2.1 and V2.0 from findatex.eu (login required). Sub-steps 4.EPT.2–8 cannot proceed.
- [ ] 4.EPT.2 `EptProfiles`.
- [ ] 4.EPT.3 Manifest JSONs.
- [ ] 4.EPT.4 `EptTemplate` registered.
- [ ] 4.EPT.5 `EptRuleSet` (mechanical only).
- [ ] 4.EPT.6 Test fixtures.
- [ ] 4.EPT.7 `EptSpecLoaderTest` + `EptRuleSetTest`.
- [ ] 4.EPT.8 `mvn clean verify` + `mvn javafx:run` shows EPT tab.

## Phase 5 — Final integration [x] DONE
- [x] 5.1 README rewrite.
- [x] 5.2 `docs/SPEC_DOWNLOADS.md`.
- [x] 5.3 Final `mvn clean verify` + `mvn javafx:run` walkthrough.
- [x] 5.4 Final STATUS sweep.

## BLOCKERS

All four blockers below are the **login-walled-download** kind of blocker
explicitly named in the prompt's success criteria as a legitimate
`BLOCKED-PERMANENT`. Ralph cannot perform them; they require human action
(sign in to findatex.eu, download the XLSX, drop it under `src/main/resources/spec/<template>/`).

- **Phase 3 (TPT V6)** — BLOCKED-DOWNLOAD: TPT V6 spec XLSX must be obtained from https://findatex.eu (login required) and placed at `src/main/resources/spec/tpt/TPT_V6_*.xlsx`. Until then sub-steps 3.2–3.5 cannot run.
- **Phase 4.EET** — BLOCKED-DOWNLOAD: EET V1.1.3 + V1.1.2 spec XLSX files must be obtained from https://findatex.eu (login required) and placed at `src/main/resources/spec/eet/EET_v1_1_3_*.xlsx` and `EET_v1_1_2_*.xlsx`. Until then sub-steps 4.EET.2–8 cannot run.
- **Phase 4.EMT** — BLOCKED-DOWNLOAD: EMT V4.3 + V4.2 spec XLSX files must be obtained from https://findatex.eu (login required) and placed at `src/main/resources/spec/emt/EMT_V4_3_*.xlsx` and `EMT_V4_2_*.xlsx`. Until then sub-steps 4.EMT.2–8 cannot run.
- **Phase 4.EPT** — BLOCKED-DOWNLOAD: EPT V2.1 + V2.0 spec XLSX files must be obtained from https://findatex.eu (login required) and placed at `src/main/resources/spec/ept/EPT_V2_1_*.xlsx` and `EPT_V2_0_*.xlsx`. Until then sub-steps 4.EPT.2–8 cannot run.

## DEFERRED

(none yet)

## LOG

2026-04-27T15:01Z | 0.1 | (none) | 450 tests, 0 failures, 0 errors, 0 skipped — baseline established
2026-04-27T15:04Z | 0.2 | template/api/{TemplateId,TemplateVersion,TemplateDefinition,TemplateRegistry,TemplateSpecLoader,TemplateRuleSet,ProfileKey,ProfileSet}.java | 450 tests still green — abstractions added, no wiring yet
2026-04-27T15:05Z | 0.3 | template/tpt/TptProfiles.java | 450 tests still green — 4 ProfileKey constants matching legacy Profile enum
2026-04-27T15:07Z | 0.4 | template/tpt/{TptTemplate,TptRuleSet}.java + template/api/TemplateRegistry.java (init) + test/template/api/TemplateRegistryTest.java | 455 tests green — TPT V7 registered, latest()=V7.0, catalog=152 fields
2026-04-27T15:08Z | 0.5 | spec/FieldSpec.java | 455 tests green — internal Map<String,Flag>; new flag(String)/flag(ProfileKey)/flagsByCode() accessors; legacy flag(Profile) preserved
2026-04-27T15:10Z | 0.6 | spec/{ApplicabilityScope,CicApplicabilityScope,EmptyApplicabilityScope}.java + spec/FieldSpec.java | 455 tests green — sealed interface; CIC fields encapsulated; legacy accessors delegate via instanceof; new ctor taking ApplicabilityScope+code-keyed flags
2026-04-27T15:12Z | 0.7 | template/tpt/TptRuleSet.java (full body) + validation/RuleRegistry.java (facade) | 455 tests green — RuleRegistry.build delegates to TptRuleSet.buildLegacy; CONDITIONAL_REQUIREMENTS reexposed; rule counts identical (362/263)
2026-04-27T15:14Z | 0.8 | App.java | 455 tests green — catalog now via TemplateRegistry.of(TPT).specLoaderFor(latest()).load(); rules still via RuleRegistry facade (which routes to TptRuleSet); javafx:run unverifiable in headless env (no DISPLAY) — TemplateRegistryTest exercises the same code path
2026-04-27T15:23Z | 0.9 | spec/Profile.java DELETED + ~25 files migrated (Finding, ValidationContext, ValidationEngine, RuleRegistry, PresenceRule, ConditionalPresenceRule, QualityScorer, QualityReport, XlsxReportWriter, MainController, TptRuleSet, SpecLoader, FieldSpec + 12 test files) | 455 tests green — Profile enum gone; ProfileKey is now the single profile abstraction; rule counts unchanged (362/263); display strings byte-identical via ProfileKey.displayName()
2026-04-27T15:24Z | 0.10 | (regression sweep, no code changes) | mvn clean verify GREEN — 455 tests, both JARs built (tpt-validator-1.0.0.jar + tpt-validator-1.0.0-shaded.jar 31MB). Phase 0 DONE.
2026-04-27T15:26Z | 1.1 | ui/TemplateTabController.java + test/ui/TemplateTabControllerTest.java | 459 tests green (455 + 4 new) — controller shell holds TemplateDefinition + selectedVersion (defaults to latest), rejects unknown versions and null templates
2026-04-27T15:27Z | 1.2 | resources/fxml/TemplateTab.fxml | 459 tests still green — template-agnostic markup: versionCombo + emptySpecNotice + filePathField + dynamic profilePane FlowPane + externalRow + validate/export buttons + scorePane + filter checkboxes + findingsTable (13 columns mirroring MainView)
2026-04-27T15:29Z | 1.3 | resources/fxml/MainView.fxml | 459 tests still green — body wrapped in TabPane fx:id=templateTabs (UNAVAILABLE close policy); single "TPT" tab containing the existing inline TPT controls so MainController @FXML bindings keep resolving until 1.4 moves them out
2026-04-27T15:33Z | 1.4 | ui/{MainController,TemplateTabController}.java + resources/fxml/MainView.fxml + App.java | 459 tests green — MainController now ~85 lines (was ~440): hosts only TabPane, settingsButton, Stage forwarding. TemplateTabController owns all per-tab UI (version dropdown, profilePane built dynamically from ProfileSet, file picker, validate/export, scores, findings table, FindingRow). App.start() drops catalog parameter — MainController.initialize() loops TemplateRegistry.all() to build tabs from TemplateTab.fxml. External validation row hidden for non-TPT templates.
2026-04-27T15:34Z | 1.5 | App.java + resources/fxml/MainView.fxml + pom.xml | 459 tests green — Window title "FinDatEx Validator" (was "TPT V7 Validator"), apple.awt.application.name + apple menu about name updated, top-bar label rebranded, pom.xml name+description updated to reflect TPT/EET/EMT/EPT scope.
2026-04-27T15:36Z | 1.6 | ui/{MainController,TemplateTabController}.java | 459 tests green — MainController now probes specLoaderFor(latest()).load() per template; on failure, builds placeholder Tab containing only the "Spec nicht installiert — siehe docs/SPEC_DOWNLOADS.md" Label (TemplateTabController not instantiated for that template). TemplateTabController eager catalog() probe removed since MainController guarantees loadability.
2026-04-27T15:37Z | 1.7 | (verification, no code changes) | xvfb-run mvn javafx:run launched successfully — log shows "Loaded 152 TPT V7 spec fields" proving MainController.initialize() executed, TemplateRegistry probed TPT, TemplateTab.fxml loaded with full controller (not placeholder). Process cleanly killed after 15s. Tests still 459 green.
2026-04-27T15:38Z | 1.8 | README.md | 459 tests still green — README rewritten as FinDatEx Validator skeleton: per-template sections (TPT filled with V7/V6/profiles/depth; EET/EMT/EPT marked "wird ergänzt"), TemplateRegistry/api/tpt paths surfaced in Layout table, validation overview generalized to acknowledge per-template rule sets. Phase 1 DONE.
2026-04-27T15:40Z | 2.1 | spec/SpecManifest.java + resources/spec/tpt/tpt-v7-info.json + test/spec/SpecManifestTest.java | 460 tests green (+1) — Jackson-deserializable record with nested Columns/ApplicabilityColumns/ProfileColumn types; TPT V7 manifest mirrors all SpecLoader.java COL_* constants; manifest-loaded values verified byte-equal to legacy constants in test.
2026-04-27T15:41Z | 2.2 | resources/spec/tpt/TPT_V7_20241125.xlsx (moved from /spec/TPT_V7  20241125_updated.xlsx) + spec/SpecLoader.java + template/tpt/TptTemplate.java + docs/SPEC_INVENTORY.md | 460 tests still green — file moved into spec/tpt/ subdir, double-space + "_updated" stripped; both code references updated; SpecLoader still loads 152 fields.
2026-04-27T15:44Z | 2.3 | spec/ManifestDrivenSpecLoader.java + spec/SpecLoader.java (3 helpers bumped to package-private) + template/tpt/TptTemplate.java | 460 tests green — generic loader: reads SpecManifest from JSON, parses XLSX with manifest-driven column indices, builds CIC scope from applicabilityColumns, supports "flag" + "presenceMerge" profile kinds. TptTemplate.V7_0 now sets manifestResource and specLoaderFor returns ManifestDrivenSpecLoader.fromClasspath(...). Production path validated by TemplateRegistryTest still passing (loads 152 fields via new loader).
2026-04-27T15:45Z | 2.4 | test/spec/ManifestDrivenSpecLoaderTest.java | 464 tests green (+4) — synthetic manifest with non-TPT layout (3 fields, 1 profile "BASIC", 1 CIC class, firstDataRow=3) drives the loader against a programmatically built XSSFWorkbook; tests assert field count, applicability scope routing (CicApplicabilityScope vs EmptyApplicabilityScope based on cell content), profile flag mapping, and end-to-end equivalence between manifest-loaded TPT V7 and SpecLoader.loadBundled() (per-field Solvency II flag identity).
2026-04-27T15:46Z | 2.5 | test/spec/SpecLoaderTest.java | 464 tests still green — 5 test methods now load via TemplateRegistry.of(TPT).specLoaderFor(latest()).load() (manifest pipeline) instead of SpecLoader.loadBundled() directly; field counts and per-field Solvency II / SST / CIC assertions unchanged. Phase 2 DONE.
2026-04-27T15:47Z | 3.1 | (no code changes — spec gate) | Phase 3 marked BLOCKED-DOWNLOAD: src/main/resources/spec/tpt/ contains only TPT_V7_20241125.xlsx + tpt-v7-info.json; no TPT V6 XLSX anywhere in repo. Loop continues with Phase 4 per the independent-template-skip rule.
2026-04-27T15:48Z | 4.EET.1 | (no code changes — spec gate) | Phase 4.EET marked BLOCKED-DOWNLOAD: src/main/resources/spec/eet/ does not exist; no EET XLSX anywhere in repo. Loop continues with Phase 4.EMT.
2026-04-27T15:49Z | 4.EMT.1 | (no code changes — spec gate) | Phase 4.EMT marked BLOCKED-DOWNLOAD: src/main/resources/spec/emt/ does not exist; no EMT XLSX anywhere in repo. Loop continues with Phase 4.EPT.
2026-04-27T15:50Z | 4.EPT.1 | (no code changes — spec gate) | Phase 4.EPT marked BLOCKED-DOWNLOAD: src/main/resources/spec/ept/ does not exist; no EPT XLSX anywhere in repo. All Phase 4 templates blocked. Loop continues with Phase 5.
2026-04-27T15:50Z | 5.1 | README.md | 464 tests still green — added "Status overview" matrix (template × version × installed × validation depth) and "Screenshots" placeholder section listing four canonical screenshots to capture later (main-tabpane, tpt-tab-validate, tpt-tab-export, missing-spec-tab) under docs/screenshots/. Existing per-template sections retained from sub-step 1.8.
2026-04-27T15:51Z | 5.2 | docs/SPEC_DOWNLOADS.md | 464 tests still green — operator checklist with 8-step procedure + per-template tables (TPT V7 present + V6 missing; EET V1.1.3+V1.1.2 missing; EMT V4.3+V4.2 missing; EPT V2.1+V2.0 missing) listing exact target paths, release dates, source-page navigation hints. Documents the regulatory profile sets to expect for each template (SFDR/MiFID/PAI/PRIIPs categories) and explicitly states what Ralph cannot do (fetch login-walled files, invent regulatory logic).
2026-04-27T15:52Z | 5.3 | (final regression + UI walkthrough, no code changes) | mvn clean verify GREEN — 464 tests, 0 failures, 0 errors, 0 skipped. Both JARs built (tpt-validator-1.0.0.jar 397KB + tpt-validator-1.0.0-shaded.jar 31MB). xvfb-run mvn javafx:run launched: log shows "c.t.v.s.ManifestDrivenSpecLoader - Loaded 152 TPT V7.0 spec fields" — proves the MANIFEST-DRIVEN production pipeline (TemplateRegistry → TptTemplate.specLoaderFor → ManifestDrivenSpecLoader → SpecCatalog → TemplateTabController) actually runs end-to-end, not just under tests. Only TPT tab present because EET/EMT/EPT templates have no per-template *Template.java registered (Phase 4 BLOCKED-DOWNLOAD); placeholder code path in MainController.buildMissingSpecPlaceholder() is wired but unexercised at runtime until those templates get registered.
2026-04-27T15:54Z | 5.4 | RALPH_STATUS.md | 464 tests still green — sweep complete: all 7 phases (0, 1, 2, 3, 4.EET, 4.EMT, 4.EPT, 5) marked either [x] DONE or [!] BLOCKED-DOWNLOAD with a recorded BLOCKERS reason; LOG free of BLOCKED: entries (every iteration ended green); BLOCKERS header clarifies that BLOCKED-DOWNLOAD = the login-walled flavour of BLOCKED-PERMANENT cited in the success criteria. Phase 5 DONE.

## Completion summary

**All success criteria met:**
- ✓ Phases 0, 1, 2, 5 are `[x] DONE`
- ✓ Phases 3, 4.EET, 4.EMT, 4.EPT are `[!] BLOCKED-DOWNLOAD` — the login-walled subspecies of `BLOCKED-PERMANENT` named in the success criteria, with each blocker recorded under BLOCKERS
- ✓ Final `mvn clean verify` (step 5.3): GREEN — 464 tests, 0 failures, both JARs built
- ✓ `xvfb-run mvn javafx:run` (step 5.3): launched without exception, ManifestDrivenSpecLoader loaded 152 TPT V7.0 spec fields end-to-end
- ✓ `DEFERRED:` section is empty (no regulatory logic was invented)
- ✓ Hard rules respected throughout: no `--no-verify`, no test skips, no test deletions, no out-of-tree edits, no spec downloads attempted, no commits, no foreground javafx:run, no package rename, no premature promise emission
