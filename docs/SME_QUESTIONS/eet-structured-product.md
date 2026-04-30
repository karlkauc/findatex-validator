# EET — Structured-Product Trigger Field

**Template:** EET V1.1.2 + V1.1.3
**Affected fields:** NUMs 583–588 (Structured-Product specific reporting block)
**Engine status:** ❌ blocked — trigger field unknown
**Unblocks:** Track 3 — Structured-Product conditional presence

## Context

A six-field block (NUMs 583..588, all `8000x_...` data names) exists for
Structured Products. Each field's spec comment ties it back to a
"Structured Product" predicate:

| NUM | Path | Comment |
|---|---|---|
| 583 | `80000_Use_Of_Proceeds_Asset_Pooling` | "Conditional to being a Structured Product" |
| 584 | `80010_Use_Of_Derivative_Exposure_In_Taxonomy_And_SFDR_Alignment` | "Conditional to being a Structured Product" |
| 585 | `80020_..._SFDR_Minimum_..._Sustainable_Investments` | "Conditional to 20420 is filled and 80010 is set to Y" |
| 586 | `80030_..._SFDR_Minimum_..._Derivative_Exposure_..._Sustainable_Investments` | "Conditional to 20420 is filled and 80010 is set to Y" |
| 587 | `80040_..._Sustainable_Investments_Taxonomy_Aligned` | "Conditional to 20450 is filled and 80010 is set to Y" |
| 588 | `80050_..._Derivative_Exposure_..._Taxonomy_Aligned` | "Conditional to 20450 is filled and 80010 is set to Y" |

NUMs 585–588 reference NUM 584 (`80010_...`) as their gate — that part
is mechanically wireable. **The blocker is NUMs 583 and 584:** the spec
text says "Conditional to being a Structured Product" without naming a
field that signals "this is a Structured Product".

## What we tried

Searched the bundled spec for fields whose path or definition contains
"Structured" — only matches are the 583–588 block itself and a definition
note on NUM 28 (`20050_..._SFDR_Product_Type_Eligible`):

> For funds & products not being in SFDR scope, which includes structured products

That suggests `NUM 28 = 0` (out-of-SFDR-scope) might be a *Structured
Product* indicator, but conflates structured products with all
out-of-scope products. The only other plausible candidate is the EMT
(MiFID) `00006_..._Target_Market_Y/N` block — out of scope for EET.

## What we need from the SME

1. **Which field signals "is a Structured Product"?** It might be:
   - NUM 28 (`20050_..._SFDR_Product_Type_Eligible`) with a specific
     value — if so, which value(s)?
   - A field outside the EET, requiring cross-template lookup (rule
     out — operators send EET in isolation).
   - An implicit operator convention (no formal flag) — in which case
     the rule cannot be wired and should be deleted from the open list.
2. **Is the conditional one-way or bidirectional?** I.e. for a non-
   Structured Product, must NUMs 583–588 be **empty**? If yes we'd add
   `EET-XF-STRUCTPROD-MUST-BE-ABSENT` rules in addition to the
   presence rules.
3. **Severity.** No softening clause in the spec text → ERROR proposed,
   pending your confirmation.

## Implementation effort once answered

- ≤ 1 hour for two presence rules (583, 584) gated by the trigger field,
  plus four `ConditionalRequirement`s for 585–588 already mechanically
  wireable (`NUM 20420 not blank AND NUM 584 = "Y" → NUM 585`, etc.).
- Tests follow the pattern of `EetPcdfpDriftRuleTest`.

## SME response

_(blank — fill in your answer here)_
