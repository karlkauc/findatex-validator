# Spec Inventory

Tracks which FinDatEx spec XLSX files are physically present in `src/main/resources/spec/`. Updated by Ralph at each iteration's spec-acquisition gate.

| Template | Version | Expected path | Status |
|----------|---------|---------------|--------|
| TPT  | V7.0   | `src/main/resources/spec/tpt/TPT_V7_20241125.xlsx`     | present |
| TPT  | V6.0   | `src/main/resources/spec/tpt/TPT_V6_*.xlsx`             | missing |
| EET  | V1.1.3 | `src/main/resources/spec/eet/EET_v1_1_3_*.xlsx`         | missing |
| EET  | V1.1.2 | `src/main/resources/spec/eet/EET_v1_1_2_*.xlsx`         | missing |
| EMT  | V4.3   | `src/main/resources/spec/emt/EMT_V4_3_*.xlsx`           | missing |
| EMT  | V4.2   | `src/main/resources/spec/emt/EMT_V4_2_*.xlsx`           | missing |
| EPT  | V2.1   | `src/main/resources/spec/ept/EPT_V2_1_*.xlsx`           | missing |
| EPT  | V2.0   | `src/main/resources/spec/ept/EPT_V2_0_*.xlsx`           | missing |

## Notes

- All non-TPT-V7 specs require manual download from https://findatex.eu (login required). Ralph does not attempt to download.
- When a spec is missing, the corresponding template phase is marked `BLOCKED-DOWNLOAD` in `RALPH_STATUS.md` and the related UI tab will display the placeholder message "Spec nicht installiert — siehe docs/SPEC_DOWNLOADS.md".
