#!/usr/bin/env python3
"""Generate the TPT V8 regression fixtures under samples/tpt/v8/.

The "Try an example" showcase and the numbered V7 fixtures stay on V7 (FinDatEx:
V7 for the December-2026 production, V8 from March 2027). These files pin the
V8-specific rule behaviour introduced with the final V8 spec (8 October 2026):

  01_clean_v8.xlsx             — valid V8 file: issuer/group ids on every row,
                                 LEIs in 115/119, V8 version string, no N/A field filled.
  02_na_fields_filled.xlsx     — V7-style data in N/A fields (35, 36, 54, 116, 120, 141)
                                 → NOT_APPLICABLE/<num> warnings.
  03_issuer_identifier_types.xlsx — type code 2 (VAT id) and 9 (internal) are not LEI-
                                 checked; type 1 with a bad checksum is; 115/140 are
                                 LEI-checked without any type column.
  04_closed_lists_v8.xlsx      — old securitisation code "e" (V8: e1/e2/e3) and bad
                                 codes in the new fields 150/151 → FORMAT errors.
  05_wrong_version.xlsx        — field 1000 still says V7 → XF-15.
  06_floating_rate_v8.xlsx     — floater without 35/36 is fine in V8; a floater without
                                 34 still fails XF-10 (and only on 34).

Asserted by core/src/test/java/.../validation/TptV8ExampleSamplesTest.java.
"""
from __future__ import annotations

from copy import deepcopy
from pathlib import Path

import build_examples as v7

ROOT = v7.ROOT
OUT = ROOT / "samples" / "tpt" / "v8"
OUT.mkdir(parents=True, exist_ok=True)

SPEC_MANIFEST = ROOT / "core/src/main/resources/spec/tpt/tpt-v8-info.json"
SPEC_XLSX = ROOT / "core/src/main/resources/spec/tpt/TPT_V8_20261008.xlsx"

V8_VERSION = "V8.0 (official) dated 8 October 2026"

# Field numbers carried by every fixture (header strings come from the V8 spec).
NUMS = [
    "1", "2", "3", "4", "5", "6", "7", "8", "8b", "9", "11",
    "12", "14", "15", "17", "21", "22", "23", "24", "25", "26",
    "32", "33", "34", "35", "36", "37", "38", "39", "40", "41",
    "46", "47", "48", "50", "51", "52", "54", "56",
    "115", "116", "119", "120", "131", "140", "141", "146",
    "150", "151", "1000",
]
HEADERS = v7.spec_headers(NUMS, SPEC_MANIFEST, SPEC_XLSX)
H = dict(zip(NUMS, HEADERS))          # field number → V8 header string


def row(**by_num: str) -> dict:
    """A fixture row keyed by header string, every unset column blank."""
    r = {h: "" for h in HEADERS}
    for num, value in by_num.items():
        r[H[num.lstrip("f")]] = value
    return r


ISSUER_LEI = v7.make_lei("969500TJ5KRTCJQSU9")      # checksum-valid, synthetic
GROUP_LEI = v7.make_lei("969500GROUPFRTRSY0")
SAP_LEI = "529900D6BF99LW9R2E68"                   # real
FUND_ISSUER_LEI = v7.make_lei("5299DEMOFUNDMGMT00")
FUND_GROUP_LEI = v7.make_lei("5299DEMOFUNDGROUP0")
CUSTODIAN_LEI = v7.make_lei("5299DEMOCUSTODIAN0")


def bad_lei(lei: str) -> str:
    last = lei[-1]
    return lei[:-1] + ("0" if last != "0" else "1")


PORT = dict(
    f1="FR0010000001", f2="1", f3="Demo Bond Fund V8", f4="EUR", f5="10000000",
    f6="2027-03-31", f7="2027-03-31", f8="100", f8b="100000", f9="0.20", f11="N",
    f115=FUND_ISSUER_LEI, f119=FUND_GROUP_LEI, f140=CUSTODIAN_LEI,
    f1000=V8_VERSION,
)

GOV_BOND = row(
    **PORT,
    f12="FR12", f14="FR0000571085", f15="1", f17="FR Treasury 1.5% 2030",
    f21="EUR", f22="5000000", f23="4900000", f24="5000000", f25="4900000", f26="0.5",
    f32="Fixed", f33="0.015", f38="1", f39="2030-05-25", f40="Bullet", f41="1",
    f46="French Republic", f47=ISSUER_LEI, f48="1", f50=GROUP_LEI, f51="1", f52="FR",
    f131="1",
)

EQUITY = row(
    **PORT,
    f12="DE31", f14="DE0007164600", f15="1", f17="SAP SE",
    f21="EUR", f22="3000000", f23="3000000", f24="3000000", f25="3000000", f26="0.3",
    f46="SAP SE", f47=SAP_LEI, f48="1", f50=SAP_LEI, f51="1", f52="DE",
    f131="3L", f151="0",
)

CASH = row(
    **PORT,
    f12="XL71", f14="CASH-EUR-001", f15="99", f17="Cash account EUR",
    f21="EUR", f22="2000000", f23="2000000", f24="2000000", f25="2000000", f26="0.2",
    f46="Demo Custodian Bank", f47=CUSTODIAN_LEI, f48="1", f50=CUSTODIAN_LEI, f51="1",
    f52="DE", f131="7",
)

CLEAN_ROWS = [GOV_BOND, EQUITY, CASH]


def s01_clean() -> list[dict]:
    return deepcopy(CLEAN_ROWS)


def s02_na_fields_filled() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    rows[0][H["35"]] = "BLOOMBERG"          # id type for the index — N/A in V8
    rows[0][H["36"]] = "Euribor 6 month"    # index name — N/A in V8
    rows[0][H["54"]] = "K"                  # old NACE field — N/A (use 148)
    rows[0][H["116"]] = "1"                 # fund issuer code type — N/A
    rows[1][H["120"]] = "1"                 # fund issuer group code type — N/A
    rows[2][H["141"]] = "1"                 # custodian code type — N/A
    return rows


def s03_issuer_identifier_types() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    rows[0][H["48"]] = "2"                  # external identifier: VAT id, not LEI-checked
    rows[0][H["47"]] = "FR12345678901"
    rows[0][H["115"]] = bad_lei(FUND_ISSUER_LEI)   # 115 is LEI-checked outright
    rows[1][H["48"]] = "9"                  # internal identifier (V7: "none")
    rows[1][H["47"]] = "INT-ISSUER-0001"
    rows[1][H["140"]] = bad_lei(CUSTODIAN_LEI)     # 140 is LEI-checked outright
    rows[2][H["48"]] = "1"
    rows[2][H["47"]] = bad_lei(CUSTODIAN_LEI)      # type 1 + bad checksum → LEI/47/48
    return rows


def s04_closed_lists_v8() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    rows[0][H["56"]] = "e"                  # V7 code; V8 splits it into e1/e2/e3
    rows[1][H["150"]] = "3"                 # closed list 0/1/2
    rows[1][H["151"]] = "X"                 # closed list 0/1/2
    rows[2][H["56"]] = "e2"                 # valid V8 code
    return rows


def s05_wrong_version() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    for r in rows:
        r[H["1000"]] = "V7.0 (official) dated 25 November 2024"
    return rows


def s06_floating_rate_v8() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    floater = deepcopy(GOV_BOND)
    floater[H["14"]] = v7.make_isin("FR001400ABC")
    floater[H["17"]] = "FR FRN 2031"
    floater[H["32"]] = "Floating"
    floater[H["33"]] = ""
    floater[H["34"]] = "EURIBOR6M"
    floater[H["37"]] = "0.005"              # 35/36 deliberately empty: N/A in V8
    broken = deepcopy(floater)
    broken[H["14"]] = v7.make_isin("FR001400ABD")
    broken[H["17"]] = "FR FRN 2032 (no index)"
    broken[H["34"]] = ""                    # XF-10 must fire on 34 only
    # keep Σ weight ≈ 1: split the gov-bond weight across the three bonds
    for r in (rows[0], floater, broken):
        r[H["26"]] = "0.1667"
        r[H["22"]] = r[H["24"]] = "1666667"
        r[H["23"]] = r[H["25"]] = "1650000"
    return [rows[0], floater, broken, rows[1], rows[2]]


EXAMPLES = [
    ("01_clean_v8.xlsx", s01_clean),
    ("02_na_fields_filled.xlsx", s02_na_fields_filled),
    ("03_issuer_identifier_types.xlsx", s03_issuer_identifier_types),
    ("04_closed_lists_v8.xlsx", s04_closed_lists_v8),
    ("05_wrong_version.xlsx", s05_wrong_version),
    ("06_floating_rate_v8.xlsx", s06_floating_rate_v8),
]


def main() -> int:
    for filename, factory in EXAMPLES:
        v7.write_xlsx(OUT / filename, factory(), HEADERS, sheet_title="TPT V8")
    readme = OUT / "README.md"
    readme.write_text("\n".join([
        "# TPT V8 regression fixtures",
        "",
        "Auto-generated by `tools/build_tpt_v8_samples.py` against the final TPT V8",
        "spec (8 October 2026). Not bundled into the web app — the demo file stays",
        "on V7 until the March-2027 production switches to V8. Asserted by",
        "`TptV8ExampleSamplesTest`.",
        "",
        "| File | What it demonstrates |",
        "|------|----------------------|",
        "| `01_clean_v8.xlsx` | Valid V8 file: issuer/group identifiers on every row (now M), LEIs in 115/119/140, `V8.0 (official) dated 8 October 2026`, N/A fields empty. |",
        "| `02_na_fields_filled.xlsx` | V7-style values in N/A fields 35, 36, 54, 116, 120, 141 → `NOT_APPLICABLE/<num>` warnings. |",
        "| `03_issuer_identifier_types.xlsx` | Type code `2` (VAT id) and `9` (internal) skip the LEI checksum; `1` with a corrupt checksum fails `LEI/47/48`; 115 and 140 are checked without a type column (`LEI/115`, `LEI/140`). |",
        "| `04_closed_lists_v8.xlsx` | Securitisation `e` (V8: `e1`/`e2`/`e3`) and out-of-list codes in 150/151 → `FORMAT/56`, `FORMAT/150`, `FORMAT/151`; `e2` passes. |",
        "| `05_wrong_version.xlsx` | Field 1000 still carries the V7 string → `XF-15/TPT_VERSION`. |",
        "| `06_floating_rate_v8.xlsx` | Floater with 34 + 37 but no 35/36 is clean in V8; floater without 34 fails `XF-10` on 34 only. |",
        "",
    ]), encoding="utf-8")
    print(f"Wrote {readme.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
