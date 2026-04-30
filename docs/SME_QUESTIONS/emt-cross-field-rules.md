# EMT — Cross-Field Rules (MiFID II Target Market, Costs, SRI)

**Template:** EMT V4.2 + V4.3
**Affected areas:** Target Market block (NUMs 31–47), Cost block (NUMs 62–119), SRI/risk indicators (NUMs 44–47)
**Engine status:** mechanical (FormatRule + PresenceRule + ConditionalPresenceRule) only
**Unblocks:** EmtRuleSet `TODO(emt-xf): needs SME validation` (`EmtRuleSet.java:57-64`)

## Context

The current EMT rule set wires only generic Format/Presence/Conditional
flags from the manifest. The three regulatory cross-field families
identified in `EmtRuleSet.java` are deferred to MiFID-II SME review.

## Family 1 — Negative Target Market

Fields with the "negative target market" semantics (where N = "should
not be sold to investors that …"):

| NUM | Path | Codification | Note |
|---|---|---|---|
| 39 | `03010_Compatible_With_Clients_Who_Can_Not_Bear_Capital_Loss` | Y / N / Neutral | "N for negative target" |
| (others to be enumerated by SME) | | | |

The SME-defined rule shape would be:

```
if "Eligible Investor Type" = restricted (e.g. Professional only)
   →  the corresponding negative-TM and risk-tolerance fields must be set
```

We don't have a clean trigger field identified.

### What we need

1. **Which NUM (in EMT V4.3) is the "Eligible Investor Type"?** The
   manifest shows NUMs 33–60 in the Target Market block but the
   discriminator field's NUM is not obvious from a path search.
2. **Which NUMs are the "negative target market" fields that must be
   populated when the discriminator is in a restricted state?** A
   table of triplets `(triggerValue, NUMtoEnforce, severity)` would make
   this directly wireable.

## Family 2 — Cost Arithmetic

EMT V4.3 reports gross ongoing costs at NUM 71 (`07100_..._Gross_Ongoing_Costs`,
"includes management + distribution fees, excludes transaction +
incidental + performance fees").

The candidate sum-check would relate NUM 71 to its building blocks:

| NUM | Path | Note |
|---|---|---|
| 71 | `07100_..._Gross_Ongoing_Costs` | M-flagged, the parent figure |
| 65 | `07040_..._Maximum_Entry_Cost_Acquired` | "Not included in the entry cost 07020" |
| 68 | `07070_..._Maximum_Exit_Cost_Acquired` | "Not included in the exit cost 07050" |
| 72 | `07105_..._Borrowing_Costs_Ex_Ante_UK` | "Borrowing costs are included in Ongoing costs" |

The relationship is **not** a clean sum:
- 65 / 68 are entry/exit acquired (one-off, NOT in 71 by definition).
- 72 is borrowing costs that are **included** in 71 (i.e. 72 ≤ 71).

### What we need

1. **The full list of NUMs included in gross ongoing costs (NUM 71)**
   with the RTS reference.
2. **Tolerance.** Decimals stored as floating-point will rarely match
   to the cent — RTS-typical tolerance is ±0.5 bp (5e-5) or ±1 bp.
   Confirm.
3. **Severity.** A 5 bp cost-sum mismatch is more "data quality" than
   "regulatory breach" → WARNING proposed; promote to ERROR if you've
   seen evidence of mis-reported costs being misleading to investors.

## Family 3 — SRI Consistency

NUMs 44 / 45 / 47 carry risk-tolerance values per methodology:

| NUM | Path | Codification |
|---|---|---|
| 44 | `04010_Risk_Tolerance_PRIIPS_Methodology` | 1-7 or Empty |
| 45 | `04020_Risk_Tolerance_UCITS_Methodology` | 1-7 or Empty |
| 47 | `04040_Risk_Tolerance_For_Non_PRIIPS_And_Non_UCITS_Spain` | 1-6 or Empty |

The candidate consistency rule:

```
if PRIIPs methodology is available (NUM 44 not blank)
   AND UCITS methodology is also available (NUM 45 not blank)
→  |NUM 44 - NUM 45| ≤ 1
```

But that requires methodological judgment — PRIIPs SRI and UCITS SRRI
are not always intended to align (they use different volatility windows).

### What we need

1. **Should NUM 44 and NUM 45 agree when both are populated?** And
   within what tolerance (exact / ±1 / no constraint)?
2. **Is NUM 47 always derivable from 44 or 45?** Or is it an
   independent local-jurisdiction figure?
3. **EPT SRI (NUM 31 in EPT)** — is there a cross-template consistency
   rule between EMT NUM 44 and EPT NUM 31? Out of scope for this brief
   (validator does not currently cross-check templates), but worth
   flagging if the answer is "yes" for future work.

## Implementation effort once answered

Each family is its own ≤ 1 hour wiring exercise once the field tables
above are filled in. We'll need new predicate variants for Family 2
(absolute-difference / range checks) — that becomes a Track 1 follow-up
if the cost-sum check is approved.

## SME response

_(blank — fill in your answer per family)_
