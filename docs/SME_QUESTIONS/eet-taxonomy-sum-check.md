# EET — Taxonomy Attribution Sum-Check

**Template:** EET V1.1.2 + V1.1.3
**Affected fields:** NUMs 41–48 (Art-8 + Art-9 sustainable-investment minimums and their sub-attributions)
**Severity proposed:** open — currently no hard sum-check
**Engine status:** soft "any-of" rule already wired (`EET-XF-ART8-MIN-SI-SPLIT`, `EET-XF-ART9-MIN-ENV-SPLIT`)
**Unblocks:** potential hardening of taxonomy-attribution validation

## Context

For Art-8 products (NUM 27 = "8") with sustainable investments
(NUM 40 = "Y"), the minimum proportion is reported in NUM 41
(`20180_..._Minimal_Proportion_Of_Sustainable_Investments_Art_8`,
floating decimal 0..1). The spec then asks **three Y/N flags** about
which categories the minimum covers:

| NUM | Path | Y/N meaning |
|---|---|---|
| 42 | `20190_..._Sustainable_Investment_EU_Taxonomy_Art_8` | Does the NUM-41 minimum include EU-Taxonomy SI? |
| 43 | `20200_..._Sustainable_Investment_Environmental_Not_EU_Taxonomy_Art_8` | Does it include Non-EU-Taxonomy environmental SI? |
| 44 | `20210_..._Sustainable_Investment_Social_Objective_Art_8` | Does it include social-objective SI? |

A symmetric block exists for Art-9 (NUMs 45 → 46, 47 + NUM 48 for social).

## Currently wired

Soft "at-least-one-of" rule:

```
if NUM 41 not blank → at least one of {42, 43, 44} must be "Y"   (WARNING)
if NUM 45 not blank → at least one of {46, 47}        must be "Y"   (WARNING)
```

Rationale: the three flags tell the reader **which categories** the
minimum covers; if all three are "N" while the minimum is non-zero, the
reported minimum is unattributed and meaningless.

## What an SME might want stricter

The `42 / 43 / 44` flags are Y/N (boolean), not values, so a literal
sum-check (`Σ component % = NUM 41`) doesn't apply at this level. The
candidate stricter rule would instead be a sum-check at the *Structured
Product* level:

```
if NUM 27 ∈ {8, 9}  →  NUM 41 ≈ Σ of attribution sub-components
                       (whichever sub-components NUMs 583..588 surface)
```

But that crosses into the Structured-Product brief
([eet-structured-product.md](eet-structured-product.md)) which has its
own open question.

## What we need from the SME

1. **Is the soft any-of rule the right level of strictness?**
   Specifically: is it possible (and lawful) for an Art-8 fund to report
   `NUM 41 = 0.30` with all three of `{42, 43, 44}` set to "N"? If yes,
   the rule should be removed or downgraded to INFO. If no, severity
   should be promoted to ERROR.
2. **Is there an RTS reference that mandates a quantitative sum-check?**
   We deliberately did not encode `42+43+44 ≥ 41` (or similar) because
   the operands are types, not values. Confirm there's no hidden
   numeric relationship we missed.
3. **Edge case — partial overlap.** EU-Taxonomy SI is by definition a
   subset of Environmental SI. If `42 = Y` then `43` may legitimately be
   "N" (the EU-Taxonomy portion already counts). Should the rule treat
   `42 = Y` as an alibi for `43`? Currently we treat them as
   independent — confirm.

## Implementation effort once answered

- "Promote to ERROR" — flip the literal in `EetRuleSet.ART8_MIN_SI_SPLIT`
  and `ART9_MIN_ENV_SPLIT`. ≤ 5 minutes.
- "Add quantitative sum-check" — requires identifying the numeric
  components (cf. structured-product brief). Estimate after that brief
  is answered; likely 1–2 hours.
- "Remove the rule" — delete the two `ConditionalAnyFieldPresenceRule`
  entries and their tests (`EetTaxonomyMinSplitRuleTest`). ≤ 15 minutes.

## SME response

_(blank — fill in your answer here)_
