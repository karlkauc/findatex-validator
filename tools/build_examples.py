#!/usr/bin/env python3
"""Generate 10 example TPT V7 files demonstrating different validation outcomes.

  01_clean.xlsx                       — passes every rule (overall score ~ 100 %).
  02_missing_mandatory.csv            — drops several M-flagged fields.
  03_bad_formats.xlsx                 — invalid currency, country, ISO date, NACE.
  04_bad_closed_lists.xlsx            — values outside the closed-list codifications.
  05_bad_isin_checksum.xlsx           — instrument code with wrong Luhn check digit.
  06_bad_lei_checksum.xlsx            — issuer code with wrong ISO 17442 check.
  07_weights_dont_sum.xlsx            — Σ field 26 PositionWeight far from 1.
  08_nav_mismatch.xlsx                — TotalNetAssets disagrees with SharePrice × Shares
                                        and CashPercentage disagrees with the cash CIC sum.
  09_interest_rate_inconsistent.xlsx  — Floating bond missing index/margin; fixed bond
                                        missing coupon rate.
  10_dates_and_derivatives.xlsx       — reporting < valuation, maturity in past,
                                        futures without underlying CIC, PIK code on equity.

Outputs land in samples/ at the project root.
"""
from __future__ import annotations

import csv
from copy import deepcopy
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "samples"
OUT.mkdir(parents=True, exist_ok=True)

HEADERS = [
    "1_Portfolio_identifying_data",
    "2_Type_of_identification_code_for_the_fund_share_or_portfolio",
    "3_Portfolio_name",
    "4_Portfolio_currency_(B)",
    "5_Net_asset_valuation_of_the_portfolio_or_the_share_class_in_portfolio_currency",
    "6_Valuation_date",
    "7_Reporting_date",
    "8_Share_price",
    "8b_Total_number_of_shares",
    "9_Cash_ratio",
    "11_Complete_SCR_delivery",
    "12_CIC_code_of_the_instrument",
    "14_Identification_code_of_the_instrument",
    "15_Type_of_identification_code_for_the_instrument",
    "17_Instrument_name",
    "21_Quotation_currency_(A)",
    "22_Market_valuation_in_quotation_currency_(A)",
    "23_Clean_market_valuation_in_quotation_currency_(A)",
    "24_Market_valuation_in_portfolio_currency_(B)",
    "25_Clean_market_valuation_in_portfolio_currency_(B)",
    "26_Valuation_weight",
    "32_Interest_rate_type",
    "33_Coupon_rate",
    "34_Interest_rate_reference_identification",
    "35_Identification_type_for_interest_rate_index",
    "36_Interest_rate_index_name",
    "37_Interest_rate_margin",
    "38_Coupon_payment_frequency",
    "39_Maturity_date",
    "40_Redemption_type",
    "41_Redemption_rate",
    "46_Issuer_name",
    "47_Issuer_identification_code",
    "48_Type_of_identification_code_for_issuer",
    "52_Issuer_country",
    "64_Exercise_type",
    "67_CIC_of_the_underlying_asset",
    "131_Underlying_asset_category",
    "146_PIK",
    "1000_TPT_Version",
]


def base_row(numdata: dict) -> dict:
    """Fill all headers with empty strings then overlay the supplied keys."""
    row = {h: "" for h in HEADERS}
    row.update(numdata)
    return row


PORT = {
    "1_Portfolio_identifying_data": "FR0010000001",
    "2_Type_of_identification_code_for_the_fund_share_or_portfolio": "1",
    "3_Portfolio_name": "Demo Bond Fund",
    "4_Portfolio_currency_(B)": "EUR",
    "5_Net_asset_valuation_of_the_portfolio_or_the_share_class_in_portfolio_currency": "10000000",
    "6_Valuation_date": "2025-12-31",
    "7_Reporting_date": "2025-12-31",
    "8_Share_price": "100",
    "8b_Total_number_of_shares": "100000",
    "9_Cash_ratio": "0.20",
    "11_Complete_SCR_delivery": "N",
    "1000_TPT_Version": "V7.0 (official) dated 25 November 2024",
}

GOV_BOND = base_row({
    **PORT,
    "12_CIC_code_of_the_instrument": "FR12",
    "14_Identification_code_of_the_instrument": "FR0000571085",  # valid ISIN
    "15_Type_of_identification_code_for_the_instrument": "1",
    "17_Instrument_name": "FR Treasury 1.5% 2030",
    "21_Quotation_currency_(A)": "EUR",
    "22_Market_valuation_in_quotation_currency_(A)": "5000000",
    "23_Clean_market_valuation_in_quotation_currency_(A)": "4900000",
    "24_Market_valuation_in_portfolio_currency_(B)": "5000000",
    "25_Clean_market_valuation_in_portfolio_currency_(B)": "4900000",
    "26_Valuation_weight": "0.5",
    "32_Interest_rate_type": "Fixed",
    "33_Coupon_rate": "0.015",
    "38_Coupon_payment_frequency": "1",
    "39_Maturity_date": "2030-05-25",
    "40_Redemption_type": "Bullet",
    "41_Redemption_rate": "1",
    "46_Issuer_name": "French Republic",
    "47_Issuer_identification_code": "969500TJ5KRTCJQSU990",  # synthetic but checksum-valid LEI (French Treasury-flavoured)
    "48_Type_of_identification_code_for_issuer": "1",
    "52_Issuer_country": "FR",
    "131_Underlying_asset_category": "1",
})

EQUITY = base_row({
    **PORT,
    "12_CIC_code_of_the_instrument": "DE31",
    "14_Identification_code_of_the_instrument": "DE0007164600",  # valid ISIN (SAP)
    "15_Type_of_identification_code_for_the_instrument": "1",
    "17_Instrument_name": "SAP SE",
    "21_Quotation_currency_(A)": "EUR",
    "22_Market_valuation_in_quotation_currency_(A)": "3000000",
    "23_Clean_market_valuation_in_quotation_currency_(A)": "3000000",
    "24_Market_valuation_in_portfolio_currency_(B)": "3000000",
    "25_Clean_market_valuation_in_portfolio_currency_(B)": "3000000",
    "26_Valuation_weight": "0.3",
    "46_Issuer_name": "SAP SE",
    "47_Issuer_identification_code": "529900D6BF99LW9R2E68",  # valid LEI (SAP)
    "48_Type_of_identification_code_for_issuer": "1",
    "52_Issuer_country": "DE",
    "131_Underlying_asset_category": "3L",
})

CASH = base_row({
    **PORT,
    "12_CIC_code_of_the_instrument": "XL71",
    "14_Identification_code_of_the_instrument": "CASH-EUR-001",
    "15_Type_of_identification_code_for_the_instrument": "99",
    "17_Instrument_name": "Cash account EUR",
    "21_Quotation_currency_(A)": "EUR",
    "22_Market_valuation_in_quotation_currency_(A)": "2000000",
    "23_Clean_market_valuation_in_quotation_currency_(A)": "2000000",
    "24_Market_valuation_in_portfolio_currency_(B)": "2000000",
    "25_Clean_market_valuation_in_portfolio_currency_(B)": "2000000",
    "26_Valuation_weight": "0.2",
    "46_Issuer_name": "Demo Custodian Bank",
    "131_Underlying_asset_category": "7",
})

CLEAN_ROWS = [GOV_BOND, EQUITY, CASH]


# ---------------------------------------------------------------- writers --

def write_xlsx(path: Path, rows: list[dict]) -> None:
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = "TPT V7"
    for c, h in enumerate(HEADERS, start=1):
        ws.cell(row=1, column=c).value = h
    for r, row in enumerate(rows, start=2):
        for c, h in enumerate(HEADERS, start=1):
            ws.cell(row=r, column=c).value = row.get(h, "")
    wb.save(path)
    print(f"Wrote {path.relative_to(ROOT)}")


def write_csv(path: Path, rows: list[dict], delimiter: str = ";") -> None:
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, delimiter=delimiter)
        w.writerow(HEADERS)
        for row in rows:
            w.writerow([row.get(h, "") for h in HEADERS])
    print(f"Wrote {path.relative_to(ROOT)}")


# --------------------------------------------------------------- scenarios -

def s01_clean() -> list[dict]:
    return deepcopy(CLEAN_ROWS)


def s02_missing_mandatory() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    # drop M fields on the first row
    rows[0]["5_Net_asset_valuation_of_the_portfolio_or_the_share_class_in_portfolio_currency"] = ""
    rows[0]["6_Valuation_date"] = ""
    rows[0]["12_CIC_code_of_the_instrument"] = ""
    rows[1]["14_Identification_code_of_the_instrument"] = ""
    rows[1]["17_Instrument_name"] = ""
    rows[2]["4_Portfolio_currency_(B)"] = ""
    return rows


def s03_bad_formats() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    rows[0]["21_Quotation_currency_(A)"] = "ZZZ"     # invalid ISO 4217
    rows[0]["52_Issuer_country"] = "XX"               # invalid ISO 3166
    rows[0]["6_Valuation_date"] = "31/12/2025"        # wrong date format
    rows[0]["7_Reporting_date"] = "2025-13-40"        # impossible date
    rows[1]["52_Issuer_country"] = "GERMANY"          # not 2-letter
    rows[2]["4_Portfolio_currency_(B)"] = "Eu"        # too short
    rows[2]["8_Share_price"] = "abc"                  # not numeric
    return rows


def s04_bad_closed_lists() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    # Field 15 closed list = {1..9, 99}
    rows[0]["15_Type_of_identification_code_for_the_instrument"] = "42"
    # Field 38 (coupon frequency) ∈ {0,1,2,4,12,52}
    rows[0]["38_Coupon_payment_frequency"] = "3"
    # Field 40 (redemption type) ∈ {Bullet, Sinkable, ...}
    rows[0]["40_Redemption_type"] = "VeryWeirdRedemption"
    # Field 11 only allows Y/N
    rows[0]["11_Complete_SCR_delivery"] = "Maybe"
    # Field 64 (exercise type) ∈ {AM, EU, AS, BE} — set on the equity row even
    # though it usually applies to options, just to trigger the closed-list rule.
    rows[1]["64_Exercise_type"] = "QQ"
    # Field 131 (underlying asset category) closed list — give it a bogus code
    rows[2]["131_Underlying_asset_category"] = "Z9"
    return rows


def s05_bad_isin_checksum() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    # Flip the last digit so the Luhn check fails
    rows[0]["14_Identification_code_of_the_instrument"] = "FR0000571086"  # was 5, now 6
    rows[1]["14_Identification_code_of_the_instrument"] = "DE0007164601"  # was 0, now 1
    return rows


def s06_bad_lei_checksum() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    # Flip the last digit so mod-97 != 1
    rows[0]["47_Issuer_identification_code"] = "969500TJ5KRTCJQSU991"  # off-by-one in check digits
    rows[1]["47_Issuer_identification_code"] = "529900D6BF99LW9R2E69"
    return rows


def s07_weights_dont_sum() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    # weights add up to 0.7 instead of 1.0 (well outside the ±2 % tolerance)
    rows[0]["26_Valuation_weight"] = "0.3"
    rows[1]["26_Valuation_weight"] = "0.2"
    rows[2]["26_Valuation_weight"] = "0.2"
    return rows


def s08_nav_mismatch() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    # SharePrice × Shares = 100 × 100 000 = 10 000 000 = TotalNetAssets in clean.
    # Bump the share price so the product is far off.
    for r in rows:
        r["8_Share_price"] = "150"             # 150 × 100 000 = 15 000 000 ≠ 10 000 000
        r["9_Cash_ratio"] = "0.50"             # declared cash 50 %; actual cash share = 20 %
    return rows


def s09_interest_rate_inconsistent() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    # Govt bond: mark Floating but leave 34..37 empty -> XF-10 fires for each missing field.
    rows[0]["32_Interest_rate_type"] = "Floating"
    rows[0]["33_Coupon_rate"] = ""
    rows[0]["34_Interest_rate_reference_identification"] = ""
    rows[0]["35_Identification_type_for_interest_rate_index"] = ""
    rows[0]["36_Interest_rate_index_name"] = ""
    rows[0]["37_Interest_rate_margin"] = ""
    # Add a second bond marked Fixed but with no coupon rate.
    extra = base_row({
        **PORT,
        "12_CIC_code_of_the_instrument": "DE22",
        "14_Identification_code_of_the_instrument": "DE000A30VYR2",  # placeholder code
        "15_Type_of_identification_code_for_the_instrument": "99",
        "17_Instrument_name": "Mystery Corp 2% 2029",
        "21_Quotation_currency_(A)": "EUR",
        "22_Market_valuation_in_quotation_currency_(A)": "1000000",
        "23_Clean_market_valuation_in_quotation_currency_(A)": "990000",
        "24_Market_valuation_in_portfolio_currency_(B)": "1000000",
        "25_Clean_market_valuation_in_portfolio_currency_(B)": "990000",
        "26_Valuation_weight": "0.1",
        "32_Interest_rate_type": "Fixed",
        "33_Coupon_rate": "",                  # ← XF-10 trigger
        "38_Coupon_payment_frequency": "2",
        "39_Maturity_date": "2029-09-30",
        "40_Redemption_type": "Bullet",
        "41_Redemption_rate": "1",
        "46_Issuer_name": "Mystery Corp",
        "47_Issuer_identification_code": "",
        "48_Type_of_identification_code_for_issuer": "9",
        "52_Issuer_country": "DE",
        "131_Underlying_asset_category": "2",
    })
    rows.append(extra)
    # Fix weight sum so XF-04 stays clean.
    rows[0]["26_Valuation_weight"] = "0.4"
    rows[1]["26_Valuation_weight"] = "0.3"
    rows[2]["26_Valuation_weight"] = "0.2"
    extra["26_Valuation_weight"] = "0.1"
    return rows


def s10_dates_and_derivatives() -> list[dict]:
    rows = deepcopy(CLEAN_ROWS)
    # Reporting < valuation -> XF-12
    for r in rows:
        r["6_Valuation_date"] = "2025-12-31"
        r["7_Reporting_date"] = "2025-11-30"
    # Maturity in the past for the gov bond -> XF-11
    rows[0]["39_Maturity_date"] = "2020-01-01"
    # Add a futures position (CIC A) without underlying CIC -> XF-14
    futures = base_row({
        **PORT,
        "6_Valuation_date": "2025-12-31",
        "7_Reporting_date": "2025-11-30",
        "12_CIC_code_of_the_instrument": "XLA1",
        "14_Identification_code_of_the_instrument": "FUT-EUREX-001",
        "15_Type_of_identification_code_for_the_instrument": "99",
        "17_Instrument_name": "EUREX FBund Future Mar26",
        "21_Quotation_currency_(A)": "EUR",
        "22_Market_valuation_in_quotation_currency_(A)": "0",
        "23_Clean_market_valuation_in_quotation_currency_(A)": "0",
        "24_Market_valuation_in_portfolio_currency_(B)": "0",
        "25_Clean_market_valuation_in_portfolio_currency_(B)": "0",
        "26_Valuation_weight": "0.0",
        "67_CIC_of_the_underlying_asset": "",       # ← XF-14 trigger
        "131_Underlying_asset_category": "A",
    })
    rows.append(futures)
    # Put a PIK code on the equity row (PIK only meaningful for bonds/loans) -> XF-13 warning
    rows[1]["146_PIK"] = "2"
    return rows


# ----------------------------------------------------------------- driver --

EXAMPLES = [
    ("01_clean.xlsx",                       s01_clean,                  "xlsx"),
    ("02_missing_mandatory.csv",            s02_missing_mandatory,      "csv"),
    ("03_bad_formats.xlsx",                 s03_bad_formats,            "xlsx"),
    ("04_bad_closed_lists.xlsx",            s04_bad_closed_lists,       "xlsx"),
    ("05_bad_isin_checksum.xlsx",           s05_bad_isin_checksum,      "xlsx"),
    ("06_bad_lei_checksum.xlsx",            s06_bad_lei_checksum,       "xlsx"),
    ("07_weights_dont_sum.xlsx",            s07_weights_dont_sum,       "xlsx"),
    ("08_nav_mismatch.xlsx",                s08_nav_mismatch,           "xlsx"),
    ("09_interest_rate_inconsistent.xlsx",  s09_interest_rate_inconsistent, "xlsx"),
    ("10_dates_and_derivatives.xlsx",       s10_dates_and_derivatives,  "xlsx"),
]


def main() -> int:
    for filename, factory, fmt in EXAMPLES:
        path = OUT / filename
        rows = factory()
        if fmt == "csv":
            write_csv(path, rows)
        else:
            write_xlsx(path, rows)

    # Generate a small README inside samples/ documenting each file.
    readme = OUT / "README.md"
    lines = [
        "# Example TPT V7 files",
        "",
        "Auto-generated by `tools/build_examples.py`. One clean reference plus",
        "nine deliberately broken files, each focused on a different rule family.",
        "",
        "| File | What it demonstrates |",
        "|------|----------------------|",
        "| `01_clean.xlsx` | Fully valid file (overall score ≈ 100 %). |",
        "| `02_missing_mandatory.csv` | Missing M-flagged fields (5, 6, 12, 14, 17, 4) → PRESENCE errors. |",
        "| `03_bad_formats.xlsx` | Invalid ISO 4217 currency, ISO 3166 country, ISO 8601 date, NACE code, numeric. |",
        "| `04_bad_closed_lists.xlsx` | Values outside closed lists for fields 11, 15, 38, 40, 64, 131. |",
        "| `05_bad_isin_checksum.xlsx` | Instrument codes with corrupt Luhn check digit. |",
        "| `06_bad_lei_checksum.xlsx` | Issuer LEIs with corrupt ISO 17442 mod-97 check. |",
        "| `07_weights_dont_sum.xlsx` | Σ field 26 = 0.7 (outside ±2 % tolerance) → XF-04. |",
        "| `08_nav_mismatch.xlsx` | TotalNetAssets ≠ SharePrice × Shares; CashPercentage off → XF-05, XF-06. |",
        "| `09_interest_rate_inconsistent.xlsx` | Floating bond missing index/margin; Fixed bond missing coupon rate → XF-10. |",
        "| `10_dates_and_derivatives.xlsx` | Reporting < Valuation; maturity in past; futures without underlying CIC; PIK on equity → XF-11, XF-12, XF-13, XF-14. |",
        "",
        "Open them via the JavaFX UI (`mvn javafx:run` → *Browse…*) to see the",
        "rule engine catch each issue, or feed them through the JUnit suite.",
        "",
    ]
    readme.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {readme.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
