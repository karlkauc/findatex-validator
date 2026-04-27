# Multi-format Sample Files — Design

## Goal

Provide synthetic, generator-driven example files for every template the
validator currently understands (TPT, EET, EMT, EPT), in the same style as
the existing TPT V7 fixtures.

## Why

Today only TPT has sample data (`samples/01_clean.xlsx` …
`samples/11_unknown_isin_lei.xlsx`). EET, EMT and EPT have no demo
inputs, so neither manual UI smoke tests nor downstream ad-hoc checks
have anything to run against. The user wants a parallel set per format
so each template tab has the same "click here for a clean / broken
demo file" experience.

## Scope (minimum viable)

Per the user, the first cut keeps things small:

| Format | # files | What they cover |
|--------|---------|-----------------|
| TPT    | 11 (existing, **moved into `samples/tpt/`**) | unchanged |
| EET V1.1.3 | 5 | clean, missing-mandatory, bad-formats, SFDR Art-8 conditional miss, SFDR Art-9 conditional miss |
| EMT V4.3 | 3 | clean, missing-mandatory, bad-formats |
| EPT V2.1 | 3 | clean, missing-mandatory, bad-formats |

Cross-field samples for EMT/EPT are deliberately omitted — those rule
sets only run mechanical validation today (per `EmtRuleSet` /
`EptRuleSet` TODOs), so a dedicated XF sample would have nothing to
trigger. EET's 6 conditional-presence rules (`EetRuleSet.CONDITIONAL_REQUIREMENTS`)
are exercised by the SFDR Art-8 / Art-9 fixtures.

Out of scope for this iteration:
- V1.1.2 / V4.2 / V2.0 (older versions): not generated. Users wanting
  to test those versions can re-run the generator with a flag later.
- CSV variants: only XLSX. The user's confirmed scope ("reicht erst
  einmal") doesn't ask for them, and the loader handles both
  identically.

## Layout

```
samples/
  README.md                      ← top-level index pointing at sub-folders
  tpt/                           ← existing 11 files, moved unchanged
    01_clean.xlsx … 11_unknown_isin_lei.xlsx
    README.md                    ← existing per-format README, moved
  eet/
    01_clean.xlsx
    02_missing_mandatory.xlsx
    03_bad_formats.xlsx
    04_sfdr_art8_no_min.xlsx
    05_sfdr_art9_no_min.xlsx
    README.md
  emt/
    01_clean.xlsx
    02_missing_mandatory.xlsx
    03_bad_formats.xlsx
    README.md
  ept/
    01_clean.xlsx
    02_missing_mandatory.xlsx
    03_bad_formats.xlsx
    README.md
```

`samples/README.md` becomes a thin index linking to each sub-folder.

## Generator

Extend `tools/build_examples.py` to:

1. Continue producing the 11 TPT files, now writing into `samples/tpt/`.
2. Add per-format builders (`build_eet()`, `build_emt()`, `build_ept()`)
   that:
   - Read the bundled spec XLSX + manifest JSON for that format from
     `src/main/resources/spec/<tmpl>/`.
   - Emit one row of headers (the spec's `PATH` column for every
     non-section-header row).
   - Build a "clean" data row by filling every `M`-flagged field via a
     `value_for(codification)` heuristic; unflagged or `O` fields stay
     blank.
   - Generate broken variants by mutating the clean row(s):
     - `02_missing_mandatory`: blank ~5 M fields.
     - `03_bad_formats`: violate one each of ISO 4217 / ISO 8601 / ISO
       3166 / numeric / Y-or-N codifications.
     - `04_sfdr_art8_no_min` *(EET only)*: set field 27 (SFDR Product
       Type) to `8`, leave field 30 blank — triggers
       `EET-XF-ART8-MIN-LT`. Also leave field 41 blank with field 40 = Y
       to fire `EET-XF-ART8-MIN-SI`.
     - `05_sfdr_art9_no_min` *(EET only)*: set field 27 to `9`, leave
       fields 31 / 45 / 48 blank — triggers `EET-XF-ART9-MIN-LT` /
       `EET-XF-ART9-MIN-ENV` / `EET-XF-ART9-MIN-SOC`.
3. Write a per-format README mirroring the structure of the existing
   `samples/README.md`.
4. Re-write `samples/README.md` as an index.

### `value_for(codification)` heuristic

The spec's codification cell is free text but follows recognisable
patterns. Mapping (case-insensitive substring match, first hit wins):

| Pattern in cell                       | Sample value                |
|---------------------------------------|-----------------------------|
| `ISO 8601` (date+time)                | `2025-12-31  12:00:00`      |
| `ISO 8601` (date only)                | `2025-12-31`                |
| `ISO 4217`                            | `EUR`                       |
| `ISO 3166`                            | `DE`                        |
| `Y / N` or `Y/N`                      | `Y`                         |
| literal `V1.1.3`                      | `V1.1.3`                    |
| `V21 or V21UK`                        | `V21`                       |
| `V4.3`                                | `V4.3`                      |
| `Alphanum`                            | `Sample`                    |
| numeric / decimal (default)           | `1`                         |
| anything else                         | `Sample`                    |

Version-string fields (cell 1 in each format) get the literal version
token unconditionally to satisfy `EetVersionRule` / `EmtVersionRule` /
`EptVersionRule`.

## Risks / things that could go wrong

- **Unrecognised codifications.** A cell whose text doesn't match any
  pattern falls through to `"Sample"` — fine for `Alphanum`, may be
  flagged for tighter codifications. Acceptable: the goal is "clean
  enough for tab-level demo", not "every M-field passes every rule on
  every profile." If a stricter clean is needed later we can hand-tune
  per-field overrides.
- **Profile flags.** The `M`-flag in the manifest is the *primary* flag
  (column 11 for EET, 7 for EMT, 7 for EPT). Profile-specific M flags
  in other columns are not used to drive sample population in this cut
  — the demo files should pass the *default* profile selection (EET:
  SFDR_PERIODIC; EMT: EMT_BASE; EPT: PRIIPS_KID).
- **Sheet name match.** The XLSX loader's `pickSheet` prefers a sheet
  whose name contains "tpt", else first non-empty. EET/EMT/EPT samples
  go in their template's expected sheet name (e.g. `"EET"`,
  `"EMT V4.3"`, `"EPT 2.1 "`) so a future loader change that dispatches
  by template id picks them up automatically.
- **Path-column trailing whitespace.** Some EPT sheet names have a
  trailing space (`"EPT 2.1 "`) and EET's spec file is from
  `EET_V1_1_3_20260410.xlsx`. We mirror the manifest values verbatim.

## JUnit coverage

The existing `ExampleSamplesTest` is TPT-specific. Once the TPT
fixtures move into `samples/tpt/`, that test's `samplesDir()` helper
must point to the sub-folder so the existing assertions keep passing.

For the three new formats we add **one parametrised or one-per-format
JUnit test class** that loads the template's bundled spec catalog +
default profile set, runs the validation engine, and checks for the
expected rule-id finding(s). One class per format keeps the wiring
obvious (each format pulls a different catalog and a different default
profile), so we go with three small new test classes:

- `EetExampleSamplesTest` — 5 tests:
  - clean → no FORMAT/* or PRESENCE/* errors on the populated fields
  - missing-mandatory → ≥3 `PRESENCE/*` errors
  - bad-formats → at least one `FORMAT/*` error on each mutated codification
  - SFDR Art-8 → `EET-XF-ART8-MIN-LT` finding
  - SFDR Art-9 → `EET-XF-ART9-MIN-LT` finding
- `EmtExampleSamplesTest` — 3 tests: clean / missing / bad-formats.
- `EptExampleSamplesTest` — 3 tests: clean / missing / bad-formats.

Each test class follows the same shape as `ExampleSamplesTest`:
gated by `@EnabledIf` on `samplesDir().resolve("<tmpl>").isDirectory()`
so the suite stays green if the generator hasn't been run yet.

Catalog loading uses the existing template wiring:
`new EetTemplate().specLoaderFor(EetTemplate.V1_1_3).load()` and
analogues for EMT/EPT. Profile sets use each template's
`profilesFor(...)` (or `profiles().defaults()` if available) — the
implementation plan resolves the exact API call after re-reading the
template definitions.

## Non-goals

- Touching the validator code itself.
- Localisation / multi-currency variants of the clean file.

## Test plan

Manual:
1. `python3 tools/build_samples_multi.py` (or `build_examples.py`,
   whichever the implementation lands on) regenerates everything.
2. `mvn javafx:run` → switch to each tab → *Browse…* → load the
   matching `01_clean.xlsx`. Expect very few or no findings.
3. Load each `02_missing_mandatory.xlsx` → expect ≥3 PRESENCE/* errors.
4. Load `03_bad_formats.xlsx` → expect FORMAT/* errors on the mutated
   columns.
5. EET only: load `04_sfdr_art8_no_min.xlsx` → expect
   `EET-XF-ART8-MIN-LT` and/or `EET-XF-ART8-MIN-SI` findings; load
   `05_sfdr_art9_no_min.xlsx` → expect `EET-XF-ART9-*` findings.
6. Re-run the existing TPT JUnit suite (`mvn -Dtest=ExampleSamplesTest
   test`) after the path fix to confirm the moved TPT samples still
   resolve.

Automated:
- `ExampleSamplesTest` (TPT) — keeps existing assertions, now reads
  from `samples/tpt/`.
- `EetExampleSamplesTest`, `EmtExampleSamplesTest`,
  `EptExampleSamplesTest` — new classes; assertions sketched in the
  *JUnit coverage* section above.
- All four classes gated by `@EnabledIf` on the corresponding
  sub-folder existing, so a fresh checkout without sample regen still
  produces a green suite.
