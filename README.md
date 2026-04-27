# FinDatEx Validator

JavaFX desktop application that loads a [FinDatEx](https://findatex.eu) data
template file (Excel `.xlsx` or CSV) and produces a quality & conformance
report against the relevant template specification.

Each supported template lives in its own UI tab, with its own version selector
and regulatory profile checkboxes. The shared validation engine, codification
parser, finding model and Excel report writer are template-agnostic.

## Status overview

| Template | Versions configured | Spec installed | Validation depth |
|----------|---------------------|----------------|------------------|
| TPT      | V7.0 (default)      | ✓ V7.0          | full (~25 cross-field rules + presence + format + codification) |
| TPT      | V6.0                | _missing_       | _planned, see `docs/SPEC_DOWNLOADS.md`_ |
| EET      | V1.1.3, V1.1.2      | _missing_       | _planned (mechanical + DEFERRED for SFDR/Taxonomy/PAI cross-field rules)_ |
| EMT      | V4.3, V4.2          | _missing_       | _planned (mechanical + DEFERRED for MiFID II target market consistency)_ |
| EPT      | V2.1, V2.0          | _missing_       | _planned (mechanical + DEFERRED for PRIIPs scenario logic)_ |

A template whose spec XLSX is not bundled appears in the UI as a placeholder
tab: _"Spec nicht installiert — siehe docs/SPEC_DOWNLOADS.md"_. Drop the
official XLSX into `src/main/resources/spec/<template>/` and add a sibling
`*-info.json` manifest to enable it.

## Screenshots

_Placeholders — to be added once a screenshot pass is run on a system with a display._

- `docs/screenshots/main-tabpane.png` — top-level shell with all four template tabs
- `docs/screenshots/tpt-tab-validate.png` — TPT tab after running validation on a real V7 file
- `docs/screenshots/tpt-tab-export.png` — exported Excel report opened in Excel
- `docs/screenshots/missing-spec-tab.png` — placeholder tab for an uninstalled template

## Supported templates

### TPT — Tripartite Template (Solvency II)

Primary template for insurance-undertaking portfolio reporting.

- **Versions**: V7.0 (2024-11-25, default) — V6.0 _(planned, requires download from findatex.eu)_
- **Regulatory profiles**: Solvency II (M/C/O baseline), IORP / EIOPA / ECB
  (PF.06.02.24 positions/assets, PF.06.03.24 look-through, ECB Addon
  PFE.06.02.30), NW 675, SST (FINMA, opt-in)
- **Validation depth**: 142 datapoints, ~25 cross-field rules (see
  `requirements.md`); ISIN/LEI checksums; optional online cross-check against
  GLEIF & OpenFIGI

### EET — European ESG Template

ESG / SFDR / Taxonomy / PAI data exchange.

_Wird ergänzt — Phase 4.EET. Requires manual download of the V1.1.3 and V1.1.2
spec XLSX files from findatex.eu (login required); see `docs/SPEC_DOWNLOADS.md`._

### EMT — European MiFID Template

MiFID II Target Market & costs.

_Wird ergänzt — Phase 4.EMT. Requires manual download of the V4.3 and V4.2
spec XLSX files from findatex.eu; see `docs/SPEC_DOWNLOADS.md`._

### EPT — European PRIIPs Template

PRIIPs KID inputs.

_Wird ergänzt — Phase 4.EPT. Requires manual download of the V2.1 and V2.0
spec XLSX files from findatex.eu; see `docs/SPEC_DOWNLOADS.md`._

## Layout

| Path | Purpose |
|---|---|
| `pom.xml` | Maven build (Java 21, JavaFX 21, POI 5.x, Commons CSV, JUnit 5) |
| `requirements.md` | Auto-generated requirements mirroring all TPT V7 datapoints + cross-field rules |
| `tools/generate_requirements.py` | Re-generates `requirements.md` from the bundled spec xlsx |
| `tools/build_samples.py` | Builds the synthetic test samples used by JUnit |
| `src/main/java/com/tpt/validator/` | Application sources (spec, ingest, validation, report, ui) |
| `src/main/java/com/tpt/validator/template/api/` | Template-agnostic abstractions (`TemplateDefinition`, `TemplateRegistry`, `ProfileKey`, `ProfileSet`) |
| `src/main/java/com/tpt/validator/template/tpt/` | TPT-specific definitions, profiles and rule set |
| `src/main/resources/spec/` | Bundled template spec XLSX files (currently TPT V7 + PIK guidelines) |
| `src/main/resources/fxml/MainView.fxml` | Top-level shell with TabPane |
| `src/main/resources/fxml/TemplateTab.fxml` | Per-template tab markup (one instance per registered template) |
| `src/test/java/...` / `src/test/resources/sample/` | Tests + synthetic samples |
| `package/jpackage.sh` / `.bat` | Native installer build scripts (auto-detect Linux/macOS/Windows) |
| `package/icon.{png,ico,icns}` | jpackage icon assets |
| `docs/SPEC_DOWNLOADS.md` | Checklist of spec XLSX files that must be downloaded from findatex.eu |

## Build & run

```bash
# Run the JavaFX UI directly:
mvn javafx:run

# Or build a fat JAR:
mvn -DskipTests package
java -jar target/tpt-validator-1.0.0-shaded.jar

# Run all tests:
mvn test

# Re-generate the synthetic test samples (after spec changes):
python3 tools/build_samples.py

# Re-generate requirements.md from the spec:
python3 tools/generate_requirements.py

# Build a native installer (Linux .deb/.rpm, macOS app, or Windows .msi):
./package/jpackage.sh        # Linux/macOS
.\package\jpackage.bat       # Windows
```

## Validation overview

The validator walks the active template's spec catalog and emits the following
rule families. Cross-field rules are template-specific; only TPT currently has
a deep cross-field rule set (XF-01..XF-25). EET/EMT/EPT will start with
mechanical rules only (presence + format + codification + spec-explicit
conditional presence) and grow as regulatory expertise is added.

- **PRESENCE** — mandatory (`M`) field missing for an active profile (ERROR).
- **COND_PRESENCE** — conditional (`C`) field missing for an active profile
  when the row's applicability scope matches (WARNING).
- **FORMAT/<num>** — wrong codification: numeric, ISO 8601 date, ISO 4217
  currency, ISO 3166-1 alpha-2 country, NACE V2.1, CIC code, alpha/alphanumeric
  length, closed-list values (ERROR).
- **ISIN/<num>/<typeNum>**, **LEI/<num>/<typeNum>** — ID-checksum rules
  attached per template via the rule set (TPT covers instrument/issuer/group/
  underlying/fund-issuer/custodian fields).
- **XF-01..XF-25** — TPT-specific cross-field rules (SCR delivery, position
  weights, NAV consistency, coupon frequency, custodian pair, interest rate
  type, date order, maturity ≥ reporting, PIK patterns, underlying CIC
  mandatoriness, TPT version, conditional presence triggers XF-16..XF-25).
- **LEI-LIVE / ISIN-LIVE** — optional online cross-check against GLEIF and
  OpenFIGI; currently TPT only. Off by default. Works behind corporate
  HTTP/NTLM proxies via *System proxy* mode (recommended) or manual proxy with
  encrypted credentials. See `docs/superpowers/specs/2026-04-27-external-validation-gleif-openfigi-design.md`.

Quality scoring categories (weighted into an overall score 0–100 %):

- Mandatory completeness (40 %)
- Format conformance (20 %)
- Closed-list conformance (15 %)
- Cross-field consistency (15 %)
- Profile completeness average (10 %)

The Excel report (`Export Excel report…` button per tab) emits 5 sheets:
`Summary`, `Scores`, `Findings`, `Field Coverage`, `Per Position`.
