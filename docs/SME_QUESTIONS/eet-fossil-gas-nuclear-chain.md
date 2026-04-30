# EET — Fossil-Gas / Nuclear EU-Taxonomy Disclosures

**Template:** EET V1.1.2 + V1.1.3
**Affected fields:** NUMs 589–614 (`9000x_...` block, ~26 fields)
**Engine status:** ❌ blocked — trigger value ambiguous
**Unblocks:** Track 3 — EU-Taxonomy Fossil-Gas / Nuclear conditionals

## Context

Three patterns appear in the block:

### Pattern A — top-level Y/N flags (NUMs 589, 592)

| NUM | Path | Comment |
|---|---|---|
| 589 | `90000_..._Investing_In_..._Fossil_Gas_Activities` | "Conditional to product type in field 20040 or 20050" |
| 592 | `90030_..._Investing_In_..._Nuclear_Activities` | same |

### Pattern B — minimum-percentage cascades (NUMs 590, 591, 593, 594, 595, 596)

Conditional on the top-level flag being "Y":

| NUM | Path | Comment |
|---|---|---|
| 590, 591 | `90010/90020_..._Minimum_Percentage_..._Fossil_Gas_...` | "Conditional to 90000" |
| 593, 594 | `90040/90050_..._Minimum_Percentage_..._Nuclear_...` | "Conditional to 90030" |
| 595, 596 | `90060/90070_..._No_Fossil_Gas_and_Nuclear_...` | "Conditional to 90000 set to Y or 90030 set to Y" |

### Pattern C — current-percentage block (NUMs 597–614)

18 fields, all C-flagged, all with comment:

> Conditional to product type in field 20040 or 20050

## The blocker

The phrase "Conditional to product type in field 20040 or 20050" appears
in **17 distinct fields** (Pattern A + Pattern C) without specifying a
*value*. Compare with NUM 35/36 (PCDFP), where the same phrase is
followed by ", set to 8 or 9" — wired as `EET-XF-PCDFP-{35,36}` with
`{27,28} ∈ {8,9}` trigger.

The Fossil-Gas / Nuclear comments **do not specify the trigger value**.
Three plausible interpretations:

1. **Same as PCDFP**: trigger when NUM 27 OR 28 ∈ {8, 9} (Art-8 / Art-9).
   This is the most natural reading and matches the EU Disclosure RTS
   scope, but it's not in the spec text.
2. **Any non-blank product type**: trigger when NUM 27 OR 28 is set to
   anything (including 0 / 6). Operationally too noisy — would fire on
   every Art-6 fund.
3. **Specific Article-9 only**: trigger only when NUM 27 = "9". Possible
   if these disclosures are tied to Paris-aligned commitments, but
   Article 8(3) products with Taxonomy alignment also report here.

Pattern B is mechanically wireable (it references explicit NUMs:
`90000`, `90030`) and not blocked by this question.

## What we need from the SME

1. **What is the trigger value for Patterns A + C?** Most likely
   `{27, 28} ∈ {8, 9}` — confirm or specify the actual scope. The
   answer applies to all 17 fields (589, 592, 597–614).
2. **Severity.** No softening clause → ERROR proposed, pending your
   confirmation.
3. **Pattern B sanity check.** "Conditional to 90000" — we read this as
   "NUM 589 = Y → NUM 590, 591 required". Confirm the Y is required
   (not just non-blank). If "N" or any other value also satisfies the
   non-blank test, the rule semantics differ.

## Implementation effort once answered

- ≤ 1 hour for Patterns A + C: 17 single-source `ConditionalRequirement`s
  (or 17 `ConditionalAnySourceRequirement`s if `{27, 28}` is the trigger,
  identical to the PCDFP wiring).
- ≤ 30 min for Pattern B: 6 single-source `ConditionalRequirement`s
  with `EqualsAny.of("Y")` or `NotBlank` (TBD per #3 above).
- One parametrized test class in the established pattern.

## SME response

_(blank — fill in your answer here)_
