# Template-aware ValidationEngine — Design

## Goal

Make the validation pipeline obey the active template, end-to-end. Today
`ValidationEngine` is hard-wired to `TptRuleSet` via a thin `RuleRegistry`
facade, so loading an EET / EMT / EPT file in the corresponding UI tab
silently runs TPT rules over it. The template tabs already know their
own `TemplateRuleSet`; this work plumbs that through to the engine and
the report writer so non-TPT XF rules actually fire in production.

## Why

The validator's UI exposes one tab per template, each with its own
profile checkboxes and version selector — but every tab funnels through
`new ValidationEngine(catalog).validate(file, profiles)`, which calls
`RuleRegistry.build(catalog, profiles)` (a `private static final
TptRuleSet TPT_RULE_SET = new TptRuleSet()`). Result:

- EET XF rules (`EET-XF-ART8-MIN-LT`, `EET-XF-ART9-MIN-LT`, …) never
  fire in the UI even though `EetRuleSet` defines them.
- The "Field Coverage" sheet in `XlsxReportWriter` shows TPT-specific
  profile columns (Solvency II / IORP / NW 675 / SST) regardless of
  which template generated the report.
- The recently-added `*ExampleSamplesTest` classes had to bypass the
  engine through a `TemplateSampleHarness` because the engine ignored
  their template's rule set.

The architectural TODO in `RuleRegistry`'s Javadoc ("Will be removed
once ValidationEngine obtains its rules directly from a TemplateRuleSet
during the controller-level template handoff in Phase 1") is the
direct subject of this work.

## Scope

Maximum (option C from brainstorming): rule-set wiring, report writer,
registry removal, all in one cycle.

In scope:
- `ValidationEngine` accepts a `TemplateRuleSet` at construction.
- UI controller (`TemplateTabController`) passes the active template's
  rule set through.
- `RuleRegistry` is deleted; `RuleRegistryTest` becomes
  `TptRuleSetTest` (same assertions, calling `new TptRuleSet()` directly).
- `TemplateSampleHarness` is deleted; the three `*ExampleSamplesTest`
  classes instantiate the engine directly.
- `XlsxReportWriter` Field-Coverage sheet renders one column per
  profile of the active template (option a from brainstorming: all
  profiles, not the user-selected subset).
- `QualityScorer` reviewed for the same TPT-pinning pattern; adjusted
  if affected.
- New `XlsxReportWriterEetTest` smoke-asserting the EET report's
  Field-Coverage columns match `EetProfiles.ALL`.

Out of scope:
- The `external/` LEI-LIVE / ISIN-LIVE pipeline (still TPT-only by
  design — `TemplateTabController:290` already gates this on
  `template.id() == TemplateId.TPT`).
- Rule-set authoring for EMT/EPT XF rules (their `*RuleSet` classes
  still carry SME-validation TODOs; this work just makes their
  current rule sets reach the engine).
- Per-template online validation expansion.

## Architecture

### Before

```
UI / tests
  └─> new ValidationEngine(catalog).validate(file, profiles)
        └─> RuleRegistry.build(catalog, profiles)
              └─> TptRuleSet.build(catalog, profiles)   // <<< pinned
```

### After

```
UI / tests
  └─> ruleSet = template.ruleSetFor(version)
  └─> new ValidationEngine(catalog, ruleSet).validate(file, profiles)
        └─> ruleSet.build(catalog, profiles)            // template-aware
```

`RuleRegistry` is removed. The engine becomes stateless modulo
`(catalog, ruleSet)`; the per-validation construction pattern stays
identical to today.

### Constructor signature

```java
public ValidationEngine(SpecCatalog catalog, TemplateRuleSet ruleSet) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet");
}

public List<Finding> validate(TptFile file, Set<ProfileKey> activeProfiles) {
    List<Rule> rules = ruleSet.build(catalog, activeProfiles);
    // body otherwise unchanged: per-rule try/catch, FindingEnricher.enrich, log line
}
```

No second `validate(...)` overload, no builder, no template inference
from catalog. Every caller already has the rule set in scope (UI knows
its `TemplateDefinition`; tests construct their own).

### Report writer

`XlsxReportWriter` currently writes the Field-Coverage sheet with a
fixed header row `[Num, Path, Definition, Solvency II, IORP/EIOPA/ECB,
NW 675, SST, ...]` and per-row `spec.flag(TptProfiles.SOLVENCY_II)` etc.

After: `XlsxReportWriter` accepts a `ProfileSet` (the template's full
profile set, from `template.profilesFor(version)` or `template.profiles()`).
Header columns and per-row flag lookups iterate `profileSet.all()` so
each template's report renders its own profile columns.

The constructor changes from
`new XlsxReportWriter(catalog)` to `new XlsxReportWriter(catalog, profileSet)`.

`QualityScorer` is reviewed for similar pinning. If it dereferences
specific TPT profile keys (e.g. for the per-profile completeness
score), the same `ProfileSet` injection applies. If it's already
profile-agnostic, no change.

### Component map

| File | Change |
|------|--------|
| `validation/ValidationEngine.java` | Two-arg constructor; remove `RuleRegistry.build(...)` indirection |
| `validation/RuleRegistry.java` | **deleted** |
| `validation/TemplateSampleHarness.java` | **deleted** (test-only) |
| `validation/RuleRegistryTest.java` | **moved** to `template/tpt/TptRuleSetTest.java` |
| `ui/TemplateTabController.java` | Pass `template.ruleSetFor(selectedVersion)` to engine; pass `template.profilesFor(selectedVersion)` to report writer |
| `report/XlsxReportWriter.java` | `ProfileSet`-driven Field-Coverage columns |
| `report/QualityScorer.java` | Adjust if pinned to TPT profiles (review during impl) |
| `EetExampleSamplesTest.java`, `EmtExampleSamplesTest.java`, `EptExampleSamplesTest.java` | Drop harness, use engine directly |
| `ExampleSamplesTest.java` (TPT) | New constructor signature |
| `EndToEndTest.java`, `XlsxReportWriterTest.java`, `FindingEnricherTest.java`, `UserFileVerificationTest.java`, `CrossFieldRulesTest.java`, `CrossFieldBoundaryTest.java` | New constructor signature |
| `template/tpt/TptRuleSetTest.java` | **new** — content from `RuleRegistryTest`, `new TptRuleSet().build(...)` instead of `RuleRegistry.build(...)` |
| `report/XlsxReportWriterEetTest.java` | **new** — assert EET report's Field-Coverage columns match `EetProfiles.ALL` |

### Data flow

```
TemplateTabController
  ├─ TemplateDefinition (already held)
  ├─ TemplateVersion    (selectedVersion)
  ├─ SpecCatalog        (lazily loaded)
  └─ Set<ProfileKey>    (from checkboxes)
              │
              ▼
TemplateRuleSet ruleSet = template.ruleSetFor(selectedVersion)
ProfileSet     profileSet = template.profilesFor(selectedVersion)
              │
              ▼
new ValidationEngine(catalog, ruleSet)
        .validate(file, activeProfiles)
              │
              ▼
new XlsxReportWriter(catalog, profileSet)
        .write(report, outputPath)
```

The active checkboxes (`Set<ProfileKey>`) drive *which rules fire*
(via `PresenceRule` / `ConditionalPresenceRule` activation). The
template's full `ProfileSet` drives *which columns the report shows*
(audit artefact — every flag in the spec, not just the user's
selection). These two are intentionally distinct and both pass through
the engine/writer.

## Error handling

No new error paths. The engine still wraps each `rule.evaluate(ctx)`
in try/catch that logs and continues. `Objects.requireNonNull` for the
new constructor argument matches the existing pattern. If the UI tries
to validate before the spec catalog has loaded (current behavior:
silent no-op or null-cat warning), behaviour is unchanged.

## Testing

### Existing tests

Every callsite of `new ValidationEngine(CATALOG)` migrates to
`new ValidationEngine(CATALOG, new TptRuleSet())` (TPT tests) or to
the appropriate template's rule set (sample tests, future). Mechanical
edit, ~7 test files.

### Renamed test

`RuleRegistryTest.java` becomes `TptRuleSetTest.java` under
`src/test/java/com/findatex/validator/template/tpt/`. Same four
assertions, with `RuleRegistry.build(CATALOG, ALL)` swapped for
`new TptRuleSet().build(CATALOG, ALL)` and
`RuleRegistry.CONDITIONAL_REQUIREMENTS` swapped for
`TptRuleSet.CONDITIONAL_REQUIREMENTS`. The package change reflects what
the tests actually exercise.

### Sample tests

`EetExampleSamplesTest`, `EmtExampleSamplesTest`,
`EptExampleSamplesTest` drop the `TemplateSampleHarness` field. Each
holds:

```java
private static final EetTemplate TEMPLATE = new EetTemplate();
private static final SpecCatalog CATALOG =
        TEMPLATE.specLoaderFor(EetTemplate.V1_1_3).load();
private static final ValidationEngine ENGINE =
        new ValidationEngine(CATALOG, TEMPLATE.ruleSetFor(EetTemplate.V1_1_3));
private static final Set<ProfileKey> PROFILES = Set.of(EetProfiles.SFDR_PERIODIC);
```

The `run()` helper becomes `ENGINE.validate(loaded, PROFILES)` — the
exact code path the production UI will take.

### New test

`XlsxReportWriterEetTest.java` builds a one-row EET file via the same
loader the sample tests use, runs validation through the engine,
writes the Excel report to a temp file, opens it with POI, and asserts:

- The `Field Coverage` sheet's header row contains every
  `EetProfiles.ALL` display name (8 columns).
- A spot-check row (e.g. `00010_EET_Version`) shows `M` under
  `SFDR Periodic`.

Sized to mirror the existing TPT `XlsxReportWriterTest` pattern.

### Manual verification

`mvn javafx:run` → EET tab → Browse → load `samples/eet/04_sfdr_art8_no_min.xlsx`.
Expectation: the findings table shows `EET-XF-ART8-MIN-LT` and/or
`EET-XF-ART8-MIN-SI` rows. Today these are absent because the engine
runs TPT rules. After the change, they appear.

Same exercise for `samples/emt/02_missing_mandatory.xlsx` (PRESENCE
errors should reference EMT NUMs, not TPT NUMs) and
`samples/ept/03_bad_formats.xlsx`.

## Risks

- **`QualityScorer` pinning.** If the per-profile completeness category
  reaches into TPT profiles by reference rather than iterating a
  `ProfileSet`, the change becomes deeper than today's reading
  suggests. Mitigation: the implementation plan's first step is to
  re-read `QualityScorer` and decide whether it needs a `ProfileSet`
  parameter too. If so, threading it follows the same pattern as
  `XlsxReportWriter`.
- **Sheet header drift in TPT.** Renaming the Field-Coverage columns
  from `Solvency II` to `SOLVENCY_II` (or vice versa) when iterating
  `profileSet.all()` would silently change every TPT report. Mitigation:
  make `XlsxReportWriter` use the profile's `displayName()` so output
  is stable.
- **External-validation gating.** `TemplateTabController:290` already
  guards the GLEIF/OpenFIGI block on `template.id() == TemplateId.TPT`.
  This work doesn't touch that guard but should not accidentally
  loosen it.

## Non-goals

- No new XF rules for EMT/EPT (their rule sets keep their existing
  SME-validation TODOs).
- No deprecation shims for the old `ValidationEngine(catalog)`
  constructor — the project commits to main without backwards-compat
  layers; updating callers in lockstep is the pattern.
- No re-styling of the Field-Coverage sheet beyond making it
  template-aware. Report aesthetics, summary-sheet text, and
  per-position-sheet logic stay as they are.
