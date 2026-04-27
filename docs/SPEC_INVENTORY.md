# Spec Inventory

Tracks which FinDatEx spec XLSX files are physically present in `src/main/resources/spec/`.

| Template | Version | Path                                                       | Status  |
|----------|---------|------------------------------------------------------------|---------|
| TPT  | V7.0   | `src/main/resources/spec/tpt/TPT_V7_20241125.xlsx`             | present |
| TPT  | V6.0   | `src/main/resources/spec/tpt/TPT_V6_20220314.xlsx`             | present |
| EET  | V1.1.3 | `src/main/resources/spec/eet/EET_V1_1_3_20260410.xlsx`         | present |
| EET  | V1.1.2 | `src/main/resources/spec/eet/EET_V1_1_2_20231205.xlsx`         | present |
| EMT  | V4.3   | `src/main/resources/spec/emt/EMT_V4_3_20251217.xlsx`           | present |
| EMT  | V4.2   | `src/main/resources/spec/emt/EMT_V4_2_20240422.xlsx`           | present |
| EPT  | V2.1   | `src/main/resources/spec/ept/EPT_V2_1_20221012.xlsx`           | present |
| EPT  | V2.0   | `src/main/resources/spec/ept/EPT_V2_0_20220215.xlsx`           | present |

## Notes

- Source files were placed under `/specs/<TEMPLATE>/V<NN>/` by the operator and copied here with normalised filenames (no double spaces, no special characters) so the manifest resource paths stay stable.
- A spec being `present` does not imply a manifest exists — see the per-template `*-info.json` files under the same directory.
