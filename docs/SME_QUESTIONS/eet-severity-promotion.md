# EET — Severity Promotion: WARNING → ERROR

**Template:** EET V1.1.2 + V1.1.3
**Affected rules:** see table below
**Engine status:** rules are wired and firing — only severity is conservative
**Unblocks:** sharper signal in operator reports (currently soft-flagged)

## Context

Several EET cross-field rules currently emit `Severity.WARNING` because
the spec text contains a softening clause (e.g. "could be fulfilled",
"could be provided"). The validator chose WARNING to avoid
over-constraining funds whose data legitimately omits the field.

When a SFDR SME confirms the operational expectation, each rule's
severity should be promoted to `ERROR` — making the finding visible at
the same priority as a missing M-flagged field.

## Rules under review

| Rule ID | Trigger | Target | Spec softening clause |
|---|---|---|---|
| `EET-XF-ART30-MUST-BE-ABSENT` | NUM 27 = "0" | NUM 30 | (group rule, see [below](#group-eet-xf-art-must-be-absent)) |
| `EET-XF-ART31-MUST-BE-ABSENT` | NUM 27 = "0" | NUM 31 | … |
| `EET-XF-ART40-MUST-BE-ABSENT` | NUM 27 = "0" | NUM 40 | … |
| `EET-XF-ART41-MUST-BE-ABSENT` | NUM 27 = "0" | NUM 41 | … |
| `EET-XF-ART42-MUST-BE-ABSENT` … `EET-XF-ART48-MUST-BE-ABSENT` | NUM 27 = "0" | NUM 42..48 | … |
| `EET-XF-ART9-PARIS-DECARB-80` | NUM 27 = "9" | NUM 80 | "Could be fulfilled for art 8" |
| `EET-XF-ART9-PARIS-DECARB-81` | NUM 27 = "9" | NUM 81 | same as above |
| `EET-XF-PCDFP-35` | {27,28} ∈ {8,9} | NUM 35 | "Could be provided for art6 under insurers demand" |
| `EET-XF-PCDFP-36` | {27,28} ∈ {8,9} | NUM 36 | same as above |

### Group `EET-XF-ART*-MUST-BE-ABSENT`

Negative SFDR constraint: when NUM 27 = "0" (out of SFDR scope), all
Art-8 / Art-9 fields (NUMs 30, 31, 40, 41, 42, 43, 44, 45, 46, 47, 48)
must be empty. Currently WARNING because some asset managers carry
legacy data on out-of-scope products.

## What we need from the SME

For each row in the table above, answer one of:

- **Promote to ERROR** — operator must clean these up; the data is
  semantically inconsistent.
- **Keep as WARNING** — soft-flag is appropriate; legitimate edge cases
  exist.
- **Suppress entirely (downgrade to INFO or remove)** — rare; only if
  the conditional doesn't actually have a downstream impact.

For Art-9 Paris/Decarbonisation (NUMs 80/81): the spec hedge "Could be
fulfilled for art 8" suggests that *Art-8* products may legitimately
populate these fields, but that doesn't tell us whether *Art-9* may
legitimately leave them blank. If Art-9 must always populate them, the
rule should be ERROR. If there's a regulatory carve-out (e.g.
non-Paris-aligned Art-9), keep as WARNING and document the carve-out
inline so the next reviewer understands.

## Implementation effort once answered

≤ 15 minutes per severity flip — change the literal in
`EetRuleSet.CONDITIONAL_REQUIREMENTS` (and `ART_FIELDS_FORBIDDEN_WHEN_OUT_OF_SCOPE`
loop for the MUST-BE-ABSENT group). Existing tests already assert severity
explicitly; promoting from WARNING to ERROR will require updating each
matching test's `containsOnly(Severity.WARNING)` assertion in:

- `EetArt9ParisDecarbRuleTest`
- `EetNegativeSfdrConstraintRuleTest`
- `EetPcdfpDriftRuleTest`

## SME response

_(blank — fill in your answer here, ideally rule-by-rule)_
