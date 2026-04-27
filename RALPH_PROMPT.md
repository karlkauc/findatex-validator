# Ralph Loop Prompt — FinDatEx Multi-Template Extension (full plan)

## Mission

Refactor `/home/karl/webdav/tpt_test` (currently a TPT-V7-only JavaFX validator) into a multi-template FinDatEx validator covering **TPT, EET, EMT, EPT** with the **last 2 versions** of each. Drive the entire architecture plan to completion in a single Ralph loop, phase by phase. Total scope ≈ 12–16 engineer-weeks; expect 100–300 iterations. Never break green tests; never invent regulatory logic; never block on a missing spec — skip and continue.

## Sources of truth (read on every iteration)

1. **Architecture plan** (read-only):
   `/home/karl/.claude/plans/analsysiere-die-seite-https-findatex-eu-adaptive-abelson.md`
2. **Progress tracker** (you maintain):
   `/home/karl/webdav/tpt_test/RALPH_STATUS.md` — if missing, create it from the "Phase Checklist" below with every box unchecked plus an empty `BLOCKERS:` section and an empty `LOG:` section.
3. **Spec inventory** (you maintain):
   `/home/karl/webdav/tpt_test/docs/SPEC_INVENTORY.md` — one row per (template, version): `present | missing` based on what's physically in `src/main/resources/spec/`.
4. **Working dir**: `/home/karl/webdav/tpt_test/`. Never modify files outside this dir; the plan file is read-only.

## Iteration protocol

Every iteration follows the same 6 steps. Do not skip any.

### Step 1 — Read STATUS
Open `RALPH_STATUS.md`. Reconcile with reality (file listing, last `mvn test` outcome). If they disagree, trust reality and fix the tracker first.

### Step 2 — Check completion
If every phase is `[x] DONE` or `[!] BLOCKED-PERMANENT` (with a recorded reason in BLOCKERS) AND step 5.3 of Phase 5 succeeded, emit the promise (see "Success criteria") and stop. Otherwise continue.

### Step 3 — Pick the next actionable sub-step
Walk the Phase Checklist top-down. **Skip** any phase whose status is `[x] DONE` or `[!] BLOCKED-PERMANENT`. Within an active phase, pick the topmost unchecked sub-step. Two skip patterns apply:

- **Spec-acquisition gate**: if a sub-step requires a spec XLSX that is not present in `src/main/resources/spec/<template>/`, mark that phase `[!] BLOCKED-DOWNLOAD` with `needs human download from findatex.eu (login required)` in BLOCKERS, and continue at the next phase. Do not attempt to download.
- **Sequential dependency**: do not start a phase whose preceding phase is unfinished — except inside Phase 4 where the three templates (EET, EMT, EPT) are independent and may each be BLOCKED-DOWNLOAD without halting the others.

### Step 4 — Implement
One focused change per iteration. Use small, reviewable edits. Prefer extending existing patterns over inventing new ones. **Critical rule for non-TPT templates**: never invent regulatory logic (SFDR / MiFID II / PRIIPs RTS). Limit cross-field rules to what is **mechanically derivable from the spec XLSX** (presence per profile, format codification, conditional-presence based on explicit "if field X = Y then field Z is mandatory" text). Anything deeper gets a stub:

```java
// DEFERRED: requires regulatory SME — <which regulation, which fields>
```

…and a corresponding entry in `RALPH_STATUS.md` under `DEFERRED:`. The loop must NOT block on regulatory uncertainty.

### Step 5 — Verify
Run `mvn -q -DskipITs test` from `/home/karl/webdav/tpt_test`.
- If green: proceed to Step 6.
- If red: do NOT mark the sub-step done. Either fix in this iteration or leave the partial work and append a `BLOCKED:` line in `LOG:` with the first failing test name and assertion. The next iteration will resume.

Spec-acquisition skips don't require a test pass — they're the only exception.

### Step 6 — Update STATUS
- Mark the completed sub-step `[x]` (only on green test).
- Append to `LOG:` one line: `<ISO timestamp> | <sub-step id> | <files touched> | <test count>`.
- If the entire phase's sub-steps are now `[x]`, mark the phase `[x] DONE`.

## Phase Checklist (canonical work breakdown)

### Phase 0 — Foundation refactor (no paket-rename, no UI tab swap)
- [ ] 0.1 Baseline: run `mvn -q -DskipITs test`. Record exact test count.
- [ ] 0.2 Empty abstractions in `com.tpt.validator.template.api`: `TemplateId` (enum: TPT, EET, EMT, EPT), `TemplateVersion` (record), `TemplateDefinition` (interface), `TemplateRegistry` (class with empty static list), `TemplateSpecLoader` (interface), `TemplateRuleSet` (interface), `ProfileKey` (record), `ProfileSet` (class). No wiring yet.
- [ ] 0.3 `com.tpt.validator.template.tpt.TptProfiles` with four `public static final ProfileKey` constants matching the existing `Profile` enum's display names.
- [ ] 0.4 `TptTemplate` implementing `TemplateDefinition` for TPT V7, delegating to existing `SpecLoader.loadBundled()`. Register in `TemplateRegistry.init()`. Add `TemplateRegistryTest` asserting `TemplateRegistry.of(TemplateId.TPT).latest().version()` is `"V7.0"` and the catalog has ≥140 fields.
- [ ] 0.5 Generalize `FieldSpec.flags`: storage becomes `Map<String, Flag>`. Existing `flag(Profile)` accessor still works via `Profile.name()`.
- [ ] 0.6 Sealed interface `ApplicabilityScope` in `com.tpt.validator.spec` with `CicApplicabilityScope` (TPT) and `EmptyApplicabilityScope`. Move CIC fields out of `FieldSpec` into the scope; keep current accessors working via instance check.
- [ ] 0.7 `TptRuleSet implements TemplateRuleSet` containing the body of `RuleRegistry.build(...)`. `RuleRegistry` becomes a thin facade.
- [ ] 0.8 `App.start()` obtains catalog via `TemplateRegistry.of(TemplateId.TPT).latest()` and rules via `TptTemplate.ruleSetFor(version).build(...)`. Window title and UI behavior unchanged.
- [ ] 0.9 Migrate every `Profile` enum reference (Finding, XlsxReportWriter, QualityScorer, all tests) to `ProfileKey`/`TptProfiles.SOLVENCY_II` etc. Display strings byte-identical. Then delete `src/main/java/com/tpt/validator/spec/Profile.java`.
- [ ] 0.10 `mvn clean verify` green; test count equals baseline; `mvn javafx:run` launches unchanged UI (background it with a 5s timeout, then kill).

### Phase 1 — TabPane UI shell
- [ ] 1.1 `TemplateTabController(TemplateDefinition)` accepting any template definition.
- [ ] 1.2 `src/main/resources/fxml/TemplateTab.fxml` — version `ComboBox`, file picker, dynamic profile `CheckBox` `FlowPane`, validate/export buttons, results `TableView`. No template-specific markup.
- [ ] 1.3 Replace body of `MainView.fxml` with `TabPane`, one tab per `TemplateRegistry.all()` entry. Initially TPT only.
- [ ] 1.4 `MainController` becomes a shell that builds tabs from the registry; existing TPT controls move into `TemplateTabController`.
- [ ] 1.5 Window title → `"FinDatEx Validator"`. Update `apple.awt.application.name`, `pom.xml` `<name>`/`<description>`, and the macOS-related `apple.*` system properties in `App.java`.
- [ ] 1.6 If a `TemplateDefinition` has no installed spec (catalog empty / loader throws), its tab body is replaced with the message `"Spec nicht installiert — siehe docs/SPEC_DOWNLOADS.md"`.
- [ ] 1.7 `mvn javafx:run` (background, 5s timeout, kill) shows the TPT tab with current behavior preserved.
- [ ] 1.8 Sketch `README.md` with one section per template (TPT filled, others "wird ergänzt").

### Phase 2 — Manifest-driven SpecLoader
- [ ] 2.1 `com.tpt.validator.spec.SpecManifest` Jackson record per the JSON schema in the plan. Add `src/main/resources/spec/tpt/tpt-v7-info.json` next to the existing TPT V7 XLSX (column indices and sheet name from current `SpecLoader.java`).
- [ ] 2.2 Rename current TPT V7 XLSX to `TPT_V7_20241125.xlsx` (no double spaces). Update any code/tests that reference the old name.
- [ ] 2.3 `ManifestDrivenSpecLoader` class. `TptTemplate.specLoaderFor(V7)` returns one constructed from the manifest. Behavior must be byte-identical to the previous `SpecLoader.loadBundled()`.
- [ ] 2.4 `ManifestDrivenSpecLoaderTest` with a synthetic manifest + minimal synthetic XLSX (3 fields, 1 profile, 1 CIC) demonstrating column-mapping flexibility.
- [ ] 2.5 Existing `SpecLoaderTest` migrates to load via the manifest path; field count unchanged.

### Phase 3 — TPT V6 second version
- [ ] 3.1 SPEC ACQUISITION: check for `src/main/resources/spec/tpt/TPT_V6_*.xlsx`. If missing → mark Phase 3 `[!] BLOCKED-DOWNLOAD`, record `needs human download of TPT V6 spec from findatex.eu` in BLOCKERS, continue with Phase 4.
- [ ] 3.2 Author `tpt-v6-info.json`. Inspect the actual sheet header to derive column indices; do not assume V7 layout.
- [ ] 3.3 Add V6 to `TptTemplate.versions()` (V7 stays default/latest). UI dropdown shows both.
- [ ] 3.4 Parametrize `TptVersionRule` with `TemplateVersion` so it accepts the active version's expected `1000_TPT_Version` value.
- [ ] 3.5 Test: load `/home/karl/webdav/tpt_test/20260331_TPTV7_CZ0008472271_2026-03-31.xlsx` under V6 — version-mismatch finding must appear.

### Phase 4 — EET, EMT, EPT (per template, in order EET → EMT → EPT)

For each `T` in [EET, EMT, EPT], the template-specific phase is `4.<T>`. Templates are independent: each may be BLOCKED-DOWNLOAD without halting the others.

- [ ] 4.T.1 SPEC ACQUISITION: both XLSX files present in `src/main/resources/spec/<t>/` (latest two versions). If missing → `[!] BLOCKED-DOWNLOAD` for this template, record blocker, continue with the next template.
- [ ] 4.T.2 `<T>Profiles` class with `public static final ProfileKey` constants per regulatory context (read profile column headers from the actual XLSX header rows; do not invent).
- [ ] 4.T.3 Manifest JSON for each version, derived by inspecting the actual XLSX layout (sheet name, header rows, profile columns, applicability columns if any).
- [ ] 4.T.4 `<T>Template implements TemplateDefinition`; register in `TemplateRegistry`. Both versions selectable, latest default.
- [ ] 4.T.5 `<T>RuleSet`: generic rules only — `PresenceRule` per profile, `FormatRule` per codification, `ConditionalPresenceRule` for spec-explicit "if … then mandatory" clauses. Anything requiring SFDR / MiFID II / PRIIPs domain interpretation gets `// DEFERRED: …` and a `DEFERRED:` entry in STATUS. **Do not invent.**
- [ ] 4.T.6 Test fixtures: synthetic clean instance file + synthetic malformed instance file under `src/test/resources/sample/<t>/<version>/`. Build them programmatically (mirror the structure of existing TPT fixtures).
- [ ] 4.T.7 `<T>SpecLoaderTest` (loads bundled spec, asserts non-empty) and `<T>RuleSetTest` (golden findings on the synthetic malformed instance).
- [ ] 4.T.8 `mvn clean verify` green; `mvn javafx:run` (background, 5s, kill) shows the new tab populated with the version dropdown and profile checkboxes.

### Phase 5 — Final integration
- [ ] 5.1 README rewrite: per-template section, supported versions matrix, screenshot placeholders.
- [ ] 5.2 `docs/SPEC_DOWNLOADS.md` with exact filenames, version, release date, URL hint per missing spec.
- [ ] 5.3 `mvn clean verify`; `mvn javafx:run` (background, 10s, kill); confirm every present tab opens, every BLOCKED-DOWNLOAD tab shows its placeholder. Record final test count.
- [ ] 5.4 Final STATUS sweep: every phase is `[x] DONE` or `[!] BLOCKED-PERMANENT` with a clear reason. No `BLOCKED:` lines remain in `LOG:`.

## Success criteria (completion promise)

When and ONLY when:
- every phase is `[x] DONE` or `[!] BLOCKED-PERMANENT` with a reason,
- the final `mvn clean verify` of step 5.3 was green,
- `mvn javafx:run` launched without exception,

output exactly this on its own line:

<promise>FINDATEX_VALIDATOR_COMPLETE</promise>

A `BLOCKED-PERMANENT` phase counts as "done for completion" only if its blocker genuinely cannot be performed by Ralph (login-walled download, regulatory SME input). Never mark `BLOCKED-PERMANENT` to skip work that is merely tedious or hard.

## Hard rules (never violate)

- Never `--no-verify`, `-DskipTests`, `-Dmaven.test.skip=true`, or any test bypass.
- Never delete a test or weaken an assertion to make a step pass — fix the code.
- Never invent regulatory logic. For non-TPT cross-field rules: only what's mechanically in the spec; everything else gets `// DEFERRED:` and stays unimplemented.
- Never modify files outside `/home/karl/webdav/tpt_test/`. The plan file is read-only.
- Never download spec XLSXs from findatex.eu (login required) — mark BLOCKED-DOWNLOAD and skip.
- Never run `mvn javafx:run` in foreground without arranging to terminate it (background + timeout + `kill` of the recorded PID; rely on log lines for startup confirmation). The loop must not hang on a GUI process.
- Never amend, force-push, or commit unless explicitly requested. Ralph's job is code, not git history.
- Never mark a sub-step `[x]` unless `mvn -q -DskipITs test` succeeded in the same iteration (exception: spec-acquisition skips).
- Never paket-rename `com.tpt.validator` → `com.findatex.validator`. That decision is deferred to the user.
- Never start a phase whose preceding phase is unfinished, except for the independent template-skip pattern in Phase 4.
- Never emit any string containing `<promise>` other than the exact completion-criterion line, and never emit it unless every criterion above is met.

## Recovery

If you find unexpected state on iteration start (half-edited files, stray classes, failing tests on a step STATUS calls `[x]`):
1. Run `git status`; if there are uncommitted changes from a crashed iteration, attempt to make them coherent rather than reverting blindly.
2. If you can't reconcile in this iteration, append `RECOVERY-NEEDED: <what's inconsistent>` to BLOCKERS in STATUS, mark the affected sub-step `[ ]` again with a `was: <previous note>` annotation, do nothing else this iteration. The next iteration retries.
3. After **three** consecutive RECOVERY-NEEDED entries on the same sub-step, mark it `[!] BLOCKED-PERMANENT — Ralph could not converge, needs human review` and continue at the next sub-step.

## Operator note (suggested invocation)

```
/ralph-loop "Read /home/karl/webdav/tpt_test/RALPH_PROMPT.md and follow it exactly." \
  --completion-promise "FINDATEX_VALIDATOR_COMPLETE" \
  --max-iterations 300
```

Recommended max-iterations: **300** for the full plan (≈30 iterations per phase × 5 phases × 2 templates avg). If the loop runs out before completion, inspect `RALPH_STATUS.md` and either resume with a higher max or retire the loop and address remaining blockers manually.
