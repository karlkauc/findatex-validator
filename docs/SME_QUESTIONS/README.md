# SME Questions Backlog

Working documents for FinDatEx subject-matter experts. Each brief frames
**one** open validation rule that the validator core cannot wire without
expert input. Once the SME answers, the brief becomes a TDD work item
(Track 2) and gets moved into the implementation queue.

## Brief format

Every brief follows the same shape so an SME can answer in five minutes:

1. **Context** — template, version(s), affected NUMs.
2. **Spec text** — verbatim quote from the bundled FinDatEx XLSX.
3. **What we want to enforce** — proposed rule in plain English.
4. **What we need from the SME** — single concrete question(s).
5. **Implementation effort once answered** — usually "≤ 1 hour" because
   the engine machinery is already in place.

## Open briefs (priority order)

| # | Brief | Template | Unblocks |
|---|---|---|---|
| 1 | [eet-pai-coverage-mapping.md](eet-pai-coverage-mapping.md) | EET | Track 2 Item 5 — PAI value-field gating |
| 2 | [eet-severity-promotion.md](eet-severity-promotion.md) | EET | Severity-promotion of 5 currently-WARNING rules |
| 3 | [eet-taxonomy-sum-check.md](eet-taxonomy-sum-check.md) | EET | Hardening of taxonomy-attribution split |
| 4 | [eet-structured-product.md](eet-structured-product.md) | EET | NUMs 583-588 conditional presence |
| 5 | [eet-fossil-gas-nuclear-chain.md](eet-fossil-gas-nuclear-chain.md) | EET | NUMs 589-614 Taxonomy-disclosure conditionals |
| 6 | [emt-cross-field-rules.md](emt-cross-field-rules.md) | EMT | Target-market block, cost arithmetic, SRI consistency |
| 7 | [ept-cross-field-rules.md](ept-cross-field-rules.md) | EPT | Performance scenarios (RHP-gated), SRI consistency, cost components |

## Workflow

1. SME answers a brief inline (or in a reply email; the brief is the
   working ground truth).
2. The answer goes into the `## SME response` section.
3. A developer turns the brief into a TDD cycle: write test, wire rule,
   move the entry to "Wired" in the relevant audit doc
   (`docs/EET_AUDIT_V113.md` etc.).
4. Brief moves to `closed/` once shipped (kept for posterity).

## Why these are deferred

The validator deliberately ships only spec-mechanical rules. Anything
that requires regulatory interpretation (RTS reference, severity
calibration, "which field discriminates X") is parked here so the
codebase doesn't accidentally invent regulatory logic.
