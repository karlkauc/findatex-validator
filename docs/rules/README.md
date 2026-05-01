# Validation rules reference

One file per (template, version) bundled with the validator. Generated from the live `TemplateRegistry` — do not edit by hand. Regenerate via `mvn -pl core -Pdocs exec:java -Dexec.args="docs/rules"`.

## Documents

- [TPT V7.0](tpt-v7-0.md) — TPT V7.0 — 2024-11-25
- [TPT V6.0](tpt-v6-0.md) — TPT V6.0 — 2022-03-14
- [EET V1.1.3](eet-v1-1-3.md) — EET V1.1.3 — 2024-10-04
- [EET V1.1.2](eet-v1-1-2.md) — EET V1.1.2 — 2023-12-05
- [EMT V4.3](emt-v4-3.md) — EMT V4.3 — 2025-12-17
- [EMT V4.2](emt-v4-2.md) — EMT V4.2 — 2024-04-22
- [EPT V2.1](ept-v2-1.md) — EPT V2.1 — 2022-10-12
- [EPT V2.0](ept-v2-0.md) — EPT V2.0 — 2022-02-15

## How to read these documents

Each per-template document follows the same five-part structure: scoring, profiles, general rules, cross-field rules, and the per-field catalog. The general-rules section lists the engines that run on every applicable field; the per-field catalog enumerates, for each spec row, which checks can fire on it and what each costs you in the score.

