# TPT V7 Validator

JavaFX desktop application that loads a FinDatEx Tripartite Template (TPT) V7
file (Excel `.xlsx` or CSV) and produces a quality & conformance report
against the V7 specification dated 2024-11-25.

Active regulatory profiles:

- **Solvency II** (M/C/O baseline)
- **IORP / EIOPA / ECB** (PF.06.02.24 positions/assets, PF.06.03.24 look-through, ECB Addon PFE.06.02.30)
- **NW 675**

## Layout

| Path | Purpose |
|---|---|
| `pom.xml` | Maven build (Java 21, JavaFX 21, POI 5.x, Commons CSV, JUnit 5) |
| `requirements.md` | Auto-generated requirements mirroring all 142 datapoints + cross-field rules |
| `tools/generate_requirements.py` | Re-generates `requirements.md` from the bundled spec xlsx |
| `tools/build_samples.py` | Builds the synthetic test samples used by JUnit |
| `src/main/java/com/tpt/validator/` | Application sources (spec, ingest, validation, report, ui) |
| `src/main/resources/spec/` | Bundled TPT V7 spec xlsx + PIK guidelines (loaded at startup) |
| `src/test/java/...` / `src/test/resources/sample/` | Tests + synthetic samples |
| `package/jpackage.sh` / `.bat` | Native installer build scripts |

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

The validator walks the spec catalog and emits the following rule families:

- **PRESENCE** — mandatory (`M`) field missing for an active profile (ERROR).
- **COND_PRESENCE** — conditional (`C`) field missing for an active profile when the row's CIC matches (WARNING).
- **FORMAT/<num>** — wrong codification: numeric, ISO 8601 date, ISO 4217 currency, ISO 3166-1 alpha-2 country, NACE V2.1, CIC code, alpha/alphanumeric length, closed-list values (ERROR).
- **ISIN/<num>/<typeNum>** — Luhn checksum on instrument/issuer codes when the codification system field = 1 (ERROR).
- **LEI/<num>/<typeNum>** — ISO 17442 mod-97 checksum when the type field flags an LEI (ERROR).
- **XF-01..XF-15** — cross-field rules (e.g., field 11 = Y → SCR contributions mandatory, position weights sum ≈ 1, NAV ≈ price × shares, coupon frequency closed list, custodian code/type pair, interest-rate-type triggers, date order, maturity ≥ reporting, PIK case patterns, underlying CIC mandatory for derivatives, TPT version = V7).

Quality scoring categories (weighted into an overall score 0–100 %):

- Mandatory completeness (40 %)
- Format conformance (20 %)
- Closed-list conformance (15 %)
- Cross-field consistency (15 %)
- Profile completeness average (10 %)

The Excel report (`Export Excel report…` in the UI) emits 5 tabs: `Summary`,
`Scores`, `Findings`, `Field Coverage`, `Per Position`.
