# Spec Downloads — FinDatEx Template Files

This is the canonical checklist for the spec XLSX files the validator expects
to be bundled in `src/main/resources/spec/`. None of them can be downloaded
automatically — every FinDatEx download requires a free login at
[https://findatex.eu](https://findatex.eu) and the consortium does not expose
direct stable URLs.

The companion file `docs/SPEC_INVENTORY.md` mirrors what's currently present
in `core/src/main/resources/spec/`.

## Procedure

1. Sign in at [https://findatex.eu](https://findatex.eu).
2. Open the **Templates** section.
3. Locate the template + version row from the table below.
4. Download the XLSX (some downloads come as a ZIP — extract the XLSX).
5. Rename the file to the exact target filename below (so the manifest
   resource path matches).
6. Place it under `src/main/resources/spec/<template>/`.
7. Author the matching `*-info.json` manifest by inspecting the actual sheet
   header rows (use `tpt-v7-info.json` as a template). The
   `ManifestDrivenSpecLoader` will pick it up automatically.
8. Register the new `TemplateVersion` constant in the per-template
   `*Template.java` (e.g., `EetTemplate`).
9. Run `mvn test` to confirm; the spec-acquisition gate in `MainController`
   will switch the placeholder tab to a full controller-driven tab on next
   `mvn javafx:run`.

## Required files

### TPT — Tripartite Template

| Version | Release    | Target path                                                  | Status   | Source page |
|---------|------------|--------------------------------------------------------------|----------|-------------|
| V7.0    | 2024-11-25 | `src/main/resources/spec/tpt/TPT_V7_20241125.xlsx`           | present  | findatex.eu → Templates → Tripartite Template (TPT) |
| V6.0    | 2022-03-14 | `src/main/resources/spec/tpt/TPT_V6_20220314.xlsx`           | present  | findatex.eu → Templates → Tripartite Template (TPT) → Archive |

Companion artefact: `PIK guidelines 240913.xlsx` (Payment-in-Kind loan rules,
referenced by TPT V7 cross-field rule `PikRule`). Currently bundled in the
repo root; should move under `src/main/resources/spec/tpt/` together with the
spec XLSX.

### EET — European ESG Template

| Version | Release    | Target path                                                  | Status    | Source page |
|---------|------------|--------------------------------------------------------------|-----------|-------------|
| V1.1.3  | 2024-10-04 | `src/main/resources/spec/eet/EET_V1_1_3_20260410.xlsx`       | present  | findatex.eu → Templates → European ESG Template (EET) |
| V1.1.2  | 2023-12-05 | `src/main/resources/spec/eet/EET_V1_1_2_20231205.xlsx`       | present  | findatex.eu → Templates → European ESG Template (EET) → Archive |

Profiles to expect in the spec header (research from spec, do not invent):
SFDR Article 6 / 8 / 9, MiFID II ESG, Taxonomy alignment, PAI (Principal
Adverse Impacts).

### EMT — European MiFID Template

| Version | Release    | Target path                                                  | Status    | Source page |
|---------|------------|--------------------------------------------------------------|-----------|-------------|
| V4.3    | 2025-12-17 | `src/main/resources/spec/emt/EMT_V4_3_20251217.xlsx`         | present  | findatex.eu → Templates → European MiFID Template (EMT) |
| V4.2    | 2024-04-22 | `src/main/resources/spec/emt/EMT_V4_2_20240422.xlsx`         | present  | findatex.eu → Templates → European MiFID Template (EMT) → Archive |

Profiles to expect: MiFID II target market suitability tiers per jurisdiction;
V4.3 adds three optional "Value Cost Advantage" fields for structured products
in France.

### EPT — European PRIIPs Template

| Version | Release    | Target path                                                  | Status    | Source page |
|---------|------------|--------------------------------------------------------------|-----------|-------------|
| V2.1    | 2022-10-12 | `src/main/resources/spec/ept/EPT_V2_1_20221012.xlsx`         | present  | findatex.eu → Templates → European PRIIPs Template (EPT) |
| V2.0    | 2022-02-15 | `src/main/resources/spec/ept/EPT_V2_0_20220215.xlsx`         | present  | findatex.eu → Templates → European PRIIPs Template (EPT) → Archive |

Profiles to expect: PRIIPs categories 1 / 2 / 3 / 4 (per RTS scenario logic);
UK FCA compliance provisions are embedded in the spec.

## Out of scope for this repository

- Automated downloads from findatex.eu — the site is login-walled and there
  are no stable URLs, so spec acquisition is a manual operator step.
- Inventing regulatory cross-field logic for non-TPT templates. SFDR /
  MiFID II / PRIIPs cross-field rules are marked `// DEFERRED:` with a
  SME-input note in the corresponding `*RuleSet.java` until a regulatory
  expert reviews them.
