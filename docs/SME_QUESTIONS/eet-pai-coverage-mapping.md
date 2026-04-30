# EET — PAI Value-Field Coverage Mapping

**Template:** EET V1.1.2 + V1.1.3
**Affected fields:** NUMs 105, 109, 113, 117, 121, … 237 (every fourth field
in the PAI block — the "Value" measurements)
**Severity proposed:** ERROR
**Engine status:** ✅ ready (`FieldPredicate.GreaterThan` shipped this branch)
**Unblocks:** Track 2 Item 5

## Context

The PAI block consists of repeating quartets:

| offset | role | example |
|---|---|---|
| +0 | **Value** (the metric) | NUM 105 = `30020_GHG_Emissions_Scope_1_Value` |
| +1 | Considered_In_The_Investment_Strategy (Y/N) | NUM 106 = `30030_..._Considered_...` |
| +2 | **Coverage** (proportion 0..1) | NUM 107 = `30040_..._Coverage` |
| +3 | Eligible_Assets (proportion 0..1) | NUM 108 = `30050_..._Eligible_Assets` |

Every Value NUM (offset +0) carries the comment:

> Conditional to Coverage > 0%

(verified verbatim in NUMs 105, 109, 113, 117, 121, … in the bundled spec
XLSX; identical text across V1.1.2 and V1.1.3.)

The Y/N "Considered" indicator at offset +1 is already wired
(`EET-XF-PAI-{106,110,…,202}`) — see `EetRuleSet.PAI_BLOCK`.

## What we want to enforce

For each PAI quartet starting at NUM `N` (where `N ∈ {105, 109, 113, …, 237}`):

```
if NUM (N+2) > 0  →  NUM (N) must be present
```

Mechanically wireable as one `ConditionalRequirement` per Value NUM, using
`FieldPredicate.GreaterThan.of(0.0)` against the corresponding Coverage NUM.

## What we need from the SME

1. **Confirm the deterministic offset.** Is "Value at NUM N, Coverage at
   NUM N+2" correct for every quartet in the block, end to end (NUM 105
   through NUM 237)? Spot-check showed it holds for the GHG Scope 1/2/3
   quartets; we want a positive confirmation that no quartet diverges
   from the pattern.
2. **Severity.** The spec text "Conditional to Coverage > 0%" carries no
   softening clause (no "could be", no "if known"). We propose
   **Severity.ERROR**, consistent with the country-list rule
   (`EET-XF-COUNTRYLIST-{615,616}`) which has analogous spec phrasing.
   Confirm or override.
3. **Edge case — Coverage exactly 0.** The spec says `> 0%` (strict).
   Coverage = 0 should suppress the rule. Confirm that's intended (the
   alternative reading would be "if any data was reported" which would
   be `>= 0` but that fires even with no data).

## Implementation effort once answered

≤ 30 minutes. The pattern is mechanical:

```java
// pseudocode in EetRuleSet.CONDITIONAL_REQUIREMENTS
for (int valueNum : List.of(105, 109, 113, …, 237)) {
    new ConditionalRequirement(
        "EET-XF-PAI-VALUE-" + valueNum,
        String.valueOf(valueNum + 2),                  // Coverage NUM
        FieldPredicate.GreaterThan.of(0.0),
        String.valueOf(valueNum),                      // Value NUM
        Severity.ERROR);
}
```

Plus one parametrized JUnit test class with positive / zero-coverage /
blank-coverage / negative-coverage / target-already-set cases.

## SME response

_(blank — fill in your answer here)_
