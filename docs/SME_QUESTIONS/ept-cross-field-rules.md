# EPT — Cross-Field Rules (PRIIPs Performance Scenarios, SRI, Costs)

**Template:** EPT V2.0 + V2.1
**Affected areas:** Performance Scenarios (NUMs 39–58), Risk Indicators (NUMs 31–34), Costs (NUMs 72–119)
**Engine status:** mechanical only — `EptRuleSet.java:58-65` `TODO(ept-xf): needs SME validation`
**Unblocks:** PRIIPs RTS cross-field validation
**Track 1 dependency:** **`FieldPredicate.GreaterThanOrEqual`** is required for the Half-RHP rules (currently YAGNI-deferred)

## Family 1 — Performance Scenarios (RHP-gated)

Found a clean pattern. The Cat-1..4 discriminator is **NUM 19**
(`00080_Portfolio_PRIIPs_Category`, codification "1 to 4"), and the
holding-period gate is **NUM 35** (`01120_Recommended_Holding_Period`,
in years, floating decimal).

### The full scenario matrix

Each row corresponds to a PRIIPs performance scenario; each column to a
time horizon (1Y / Half-RHP / RHP-or-First-Call):

| Scenario | 1Y | Half-RHP | RHP-or-First-Call |
|---|---|---|---|
| Unfavourable | NUM 39 | NUM 40 | NUM 41 |
| Moderate | NUM 44 | NUM 45 | NUM 46 |
| Favourable | NUM 49 | NUM 50 | NUM 51 |
| Stress | NUM 54 | NUM 55 | NUM 56 |

The RHP-or-First-Call column (41, 46, 51, 56) is M-flagged — always
required. The other two columns are C-flagged with conditional comments.

### Conditional comments (verbatim)

- 1Y scenarios (NUMs 39, 44, 49, 54): "Mandatory if RHP > 1 year"
- Half-RHP scenarios (NUMs 40, 45, 50, 55): "Mandatory if the RHP >= 10 years"

### Proposed rule wiring

```
NUM 35 > 1.0   →  NUMs 39, 44, 49, 54 must be present     (uses GreaterThan)
NUM 35 >= 10.0 →  NUMs 40, 45, 50, 55 must be present     (needs GreaterThanOrEqual)
```

`FieldPredicate.GreaterThan` is shipped. **`GreaterThanOrEqual` does not
exist yet** (YAGNI-deferred). Wiring this family will require adding it
as a sealed permit + record + tests. Trivial extension once Family 1 is
prioritised.

### What we need from the SME

1. **Severity.** "Mandatory if …" is unambiguous — ERROR proposed.
   Confirm.
2. **Autocallables special-case.** Each 1Y / Half-RHP / RHP comment also
   says "autocallables only if called after 1y / ½ RHP". The autocall
   gate is at NUMs 42, 47, 52, 57 (`02032_Autocall_Applied_..._Scenario`,
   Y/N). Should the rule **suppress** the requirement when autocall = N
   for the relevant date, or do operators always populate the field
   anyway? If the latter, the simple RHP gate above is correct.
3. **Cat-1..4 difference.** NUM 19 ranges 1–4. Spec says "Performance &
   MRM calculation method, see Annex II, numbers 4-7". Does the rule
   shape (above) apply uniformly to all four categories, or do
   Categories 1 / 4 (single-payoff / autocallable) need a different
   matrix?

## Family 2 — SRI Consistency

| NUM | Path | Codification | M? |
|---|---|---|---|
| 31 | `01090_SRI` | number [1-7] | M |
| 32 | `01095_IS_SRI_Adjusted` | Y/N | M |
| 33 | `01100_MRM` | number [1-7] | M |
| 34 | `01110_CRM` | number [1-6] | M |

Per Annex II RTS, the SRI is derivable from MRM and CRM by combining
classes (the so-called "SRI grid"). Adjustment is allowed via NUM 32 = Y.

### Proposed rule

```
if NUM 32 = "N"
→  NUM 31 must equal SRI_Grid(NUM 33, NUM 34)
```

The grid lookup is a 7×6 table from Annex II.

### What we need from the SME

1. **Confirm the grid.** Provide the 7×6 lookup table (or reference
   thereto in the bundled spec — we did not find it in the EPT XLSX
   itself).
2. **Severity.** A grid mismatch is a hard regulatory breach → ERROR
   proposed.
3. **Tolerance.** SRI is integer; no tolerance question.

This will need a new rule class (not just a new predicate) because the
relationship is functional, not inequality-based. Estimated 2–3 hours
including tests.

## Family 3 — Cost Components

Cost fields (all O-flagged in V2.1):

| NUM | Path | Notes |
|---|---|---|
| 111 | `07010_Total_Cost_1_Year_Or_First_Call` | parent (1Y) |
| 113 | `07030_Total_Cost_Half_RHP` | parent (Half-RHP) |
| 115 | `07050_Total_Cost_RHP` | parent (RHP) |
| 117 | `07070_One_Off_Costs_Portfolio_Entry_Cost` | child component |
| 118 | `07080_One_Off_Costs_Portfolio_Exit_Cost` | child component |
| 119 | `07090_Ongoing_Costs_Portfolio_Transaction_Costs` | child component |

The candidate sum-check would relate the parent (115 = Total Cost RHP) to
the children — but with **time-amortisation**: one-off costs spread over
the RHP, recurring costs annualised. The arithmetic is non-trivial:

```
Total_Cost_RHP ≈ (Entry + Exit) + (Ongoing × RHP) + (Transaction × RHP)
```

### What we need from the SME

1. **Full list of components per parent.** The three children above
   are clearly only a subset of what feeds 115 — there are at least 6
   more O-flagged cost NUMs in the same neighbourhood (Indirect costs,
   Real-asset costs, Borrowing costs etc.) that *may* belong in the
   sum.
2. **Amortisation formula.** Specifically: one-off costs as
   `(Entry + Exit) / RHP` per-year, or as a flat `(Entry + Exit)` over
   the RHP horizon? RTS Annex VII has the canonical formula but we'd
   need an SME to translate it into NUM-keyed terms.
3. **Tolerance.** Likely ±1 bp (1e-4) for sum-check; confirm.
4. **Severity.** Likely WARNING (cost arithmetic mismatches are
   typically rounding artefacts unless gross).

## Implementation effort once answered

| Family | Effort | Dependencies |
|---|---|---|
| 1 (Performance) | ≤ 1.5 hours | Add `FieldPredicate.GreaterThanOrEqual` (≤ 30 min) |
| 2 (SRI grid) | 2–3 hours | New `SriGridRule` class with 7×6 lookup table |
| 3 (Costs) | 2–4 hours | New range/tolerance predicate; depends on full component list |

## SME response

_(blank — fill in your answer per family)_
