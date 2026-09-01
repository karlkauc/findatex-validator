"""The TPT showcase fixture: three funds, 60 positions, ~90 spec fields.

Why this lives apart from ``build_examples.py``: the numbered fixtures there
are one-idea-per-file regression material (three rows, one broken rule each).
The showcase has the opposite job — it is what a first-time visitor sees when
they click *"No file at hand? Try an example"*, so it has to look like a real
delivery from an asset manager: a proper multi-asset book, portfolio-level
figures that actually reconcile, and a realistic handful of defects rather
than one deliberate bug.

Everything that can be derived is derived, not typed:

* ``NAV = Σ MarketValuePC`` (field 5)
* ``PositionWeight = MarketValuePC / NAV`` (field 26)
* ``SharePrice = NAV / TotalNumberOfShares`` (field 8)
* ``CashPercentage = Σ MarketValuePC of CIC xx7x / NAV`` (field 9)

so XF-04 (weight sum), XF-05 (cash ratio) and XF-06 (NAV) are consistent by
construction and only fire where :data:`CELL_DEFECTS` says they should.

Identifier honesty — read before adding instruments:

* Equity, ETF and OAT ISINs are the real, publicly published ones.
* Bond and derivative ISINs are **constructed**: checksum-valid and in a
  plausible national range, but not registered anywhere.
* LEIs are **constructed** except SAP's, which is the real one. They pass the
  ISO 17442 mod-97 check but will not resolve against GLEIF, so switching
  online validation on will flag them — that is expected, not a bug.

``assert_identifiers()`` recomputes every Luhn / mod-97 check digit at
generation time, so an accidental typo fails the generator instead of showing
up as a mystery finding in the demo. Only the two codes derived into
:data:`BROKEN_ISIN` / :data:`BROKEN_LEI` are allowed to be wrong.
"""
from __future__ import annotations

from typing import Callable

# ---------------------------------------------------------------- columns --
# Field numbers the showcase populates, in delivery order. Kept as numbers
# only; build_examples.py turns them into the "<num>_<path>" header strings
# the spec itself uses, so a header can never drift from the spec text.
#
# Deliberately absent, because including them would fire a cross-field rule
# that has nothing to demonstrate here:
#   29  → XF-16 would then require 31
#   138 → XF-25 would then require 139
SHOWCASE_NUMS: list[str] = [
    # portfolio / share class
    "1", "2", "3", "4", "5", "6", "7", "8", "8b", "9", "10", "11",
    # position core
    "12", "13", "14", "15", "17", "18", "19", "21", "22", "23", "24", "25",
    "26", "27", "28", "30",
    # bond characteristics
    "32", "33", "34", "35", "36", "37", "38", "39", "40", "41",
    "42", "43", "44", "45",
    # issuer / credit risk
    "46", "47", "48", "49", "50", "51", "52", "53", "54", "55", "57", "148",
    # derivatives + underlying
    "20", "60", "61", "64", "65", "67", "68", "69", "70", "71", "72", "74",
    "80", "81", "82", "83", "84", "85", "86",
    # analytics
    "90", "91", "92", "93",
    # QRT position information
    "106", "107", "108", "110", "112", "113",
    # QRT portfolio information (fund issuer / custodian block)
    "115", "116", "117", "118", "119", "120", "121", "122", "123", "123a",
    "124", "126", "133", "137", "140", "141",
    # classification + version
    "131", "1000",
]

VALUATION_DATE = "2025-12-31"
REPORTING_DATE = "2025-12-31"
TPT_VERSION = "V7.0 (official) dated 25 November 2024"

# --------------------------------------------------------------- issuers ---
# name, LEI, country, NACE (field 54 / 148), issuer-group name + LEI,
# and the ESA 2010 counterparty sector for field 137.
# NACE codes follow the FormatRule pattern ^[A-U]\d{0,4}$ (no dots).
ISSUERS: dict[str, dict] = {
    "REPUBLIQUE FRANCAISE":      dict(lei="969500TJ5KRTCJQSU990", country="FR", nace="O8411", group="REPUBLIQUE FRANCAISE", sector="10"),
    "BUNDESREPUBLIK DEUTSCHLAND": dict(lei="529900MBHTLGWCUCVS41", country="DE", nace="O8411", group="BUNDESREPUBLIK DEUTSCHLAND", sector="10"),
    "REPUBBLICA ITALIANA":       dict(lei="529900TDSLR4AGQGVE90", country="IT", nace="O8411", group="REPUBBLICA ITALIANA", sector="10"),
    "REINO DE ESPANA":           dict(lei="529900GRZ2BQY5ZM9N49", country="ES", nace="O8411", group="REINO DE ESPANA", sector="10"),
    "KONINKRIJK DER NEDERLANDEN": dict(lei="5299001QNPFQEHKGMT63", country="NL", nace="O8411", group="KONINKRIJK DER NEDERLANDEN", sector="10"),
    "REPUBLIK OESTERREICH":      dict(lei="529900JQBBQ9WFWG4W14", country="AT", nace="O8411", group="REPUBLIK OESTERREICH", sector="10"),
    "SAP SE":                    dict(lei="529900D6BF99LW9R2E68", country="DE", nace="J6201", group="SAP SE", sector="9"),
    "SIEMENS AG":                dict(lei="529900MHTJPQFH1WFN68", country="DE", nace="C2811", group="SIEMENS AG", sector="9"),
    "ALLIANZ SE":                dict(lei="529900PL3D2QXCRZTL45", country="DE", nace="K6512", group="ALLIANZ SE", sector="7"),
    "DEUTSCHE TELEKOM AG":       dict(lei="529900PZ5C1WKJQC2P25", country="DE", nace="J6110", group="DEUTSCHE TELEKOM AG", sector="9"),
    "BASF SE":                   dict(lei="5299005N9JNTGJZ4KQ88", country="DE", nace="C2011", group="BASF SE", sector="9"),
    "NESTLE SA":                 dict(lei="5299009B2PGWTFQ4LX04", country="CH", nace="C1089", group="NESTLE SA", sector="9"),
    "NOVARTIS AG":               dict(lei="529900MHTJPQ4RGZTL13", country="CH", nace="C2120", group="NOVARTIS AG", sector="9"),
    "ROCHE HOLDING AG":          dict(lei="529900FQKSVPHRWQ2W29", country="CH", nace="C2120", group="ROCHE HOLDING AG", sector="9"),
    "ASML HOLDING NV":           dict(lei="529900GDF5B2WLQ4RM58", country="NL", nace="C2611", group="ASML HOLDING NV", sector="9"),
    "AIRBUS SE":                 dict(lei="529900GBQ4TXNMRQCZ29", country="NL", nace="C3030", group="AIRBUS SE", sector="9"),
    "LVMH SE":                   dict(lei="529900RLQ7RGWZ9J4L71", country="FR", nace="C1520", group="LVMH SE", sector="9"),
    "TOTALENERGIES SE":          dict(lei="529900G3RCC6TDPZ7F52", country="FR", nace="B0610", group="TOTALENERGIES SE", sector="9"),
    "SANOFI SA":                 dict(lei="529900QJ9LBTBQ4RMD14", country="FR", nace="C2120", group="SANOFI SA", sector="9"),
    "AIR LIQUIDE SA":            dict(lei="5299002JFTBWGZ4RCK63", country="FR", nace="C2011", group="AIR LIQUIDE SA", sector="9"),
    "AXA SA":                    dict(lei="529900FQBWJ7RM4RTL75", country="FR", nace="K6512", group="AXA SA", sector="7"),
    "UNILEVER PLC":              dict(lei="529900VRQ8WPHZ4JT981", country="GB", nace="C1089", group="UNILEVER PLC", sector="9"),
    "ASTRAZENECA PLC":           dict(lei="5299005QMTQZ9JQ3CL43", country="GB", nace="C2120", group="ASTRAZENECA PLC", sector="9"),
    "ENEL SPA":                  dict(lei="529900HMTQ9JZ4RM7F65", country="IT", nace="D3511", group="ENEL SPA", sector="9"),
    "IBERDROLA SA":              dict(lei="529900MTQZ4RMFQJ8L95", country="ES", nace="D3511", group="IBERDROLA SA", sector="9"),
    "APPLE INC":                 dict(lei="529900GRZ4RMTQJ9WL82", country="US", nace="C2620", group="APPLE INC", sector="9"),
    "MICROSOFT CORP":            dict(lei="529900TQJ4RMGZWL9F58", country="US", nace="J6201", group="MICROSOFT CORP", sector="9"),
    "NVIDIA CORP":               dict(lei="5299004RMTQJZGWL9N27", country="US", nace="C2611", group="NVIDIA CORP", sector="9"),
    "BLACKROCK ASSET MANAGEMENT IRELAND": dict(lei="529900JQ4RMTGZWL9B25", country="IE", nace="K6430", group="BLACKROCK INC", sector="4"),
    "VANGUARD GROUP IRELAND":    dict(lei="529900MQ4RTJZGWL9C42", country="IE", nace="K6430", group="VANGUARD GROUP", sector="4"),
    "EUREX CLEARING AG":         dict(lei="529900TJQ4RMGZWL9D49", country="DE", nace="K6611", group="DEUTSCHE BOERSE AG", sector="5"),
    "STOXX LTD":                 dict(lei="529900QZ4RMTGJWL9E19", country="CH", nace="K6611", group="DEUTSCHE BOERSE AG", sector="5"),
    "DEMO CUSTODIAN BANK SA":    dict(lei="529900WL9GZRMTQJ4B19", country="LU", nace="K6419", group="DEMO CUSTODIAN GROUP", sector="2"),
}

# ----------------------------------------------------------- instruments ---
# One entry per security. ``kind`` drives which block of fields a row fills:
#   govbond / corpbond → bond characteristics (32..45) + credit risk (46..57)
#   equity / fund      → credit risk only
#   future / option    → derivative block (60..71), no issuer
#   cash               → neither
INSTRUMENTS: dict[str, dict] = {
    # --- government bonds -------------------------------------------------
    "OAT_2032": dict(kind="govbond", cic="FR11", isin="FR0000571085", name="OAT 2.50% 25/05/2032",
                     issuer="REPUBLIQUE FRANCAISE", ccy="EUR", uac="1", rate_type="Fixed",
                     coupon="0.025", freq="1", maturity="2032-05-25", price=0.9840, duration=5.8),
    "BUND_2034": dict(kind="govbond", cic="DE11", isin="DE000BU2Z023", name="BUNDESANLEIHE 2.60% 15/08/2034",
                      issuer="BUNDESREPUBLIK DEUTSCHLAND", ccy="EUR", uac="1", rate_type="Fixed",
                      coupon="0.026", freq="1", maturity="2034-08-15", price=1.0125, duration=7.6),
    "BTP_2033": dict(kind="govbond", cic="IT11", isin="IT0005428617", name="BTP 4.40% 01/05/2033",
                     issuer="REPUBBLICA ITALIANA", ccy="EUR", uac="1", rate_type="Fixed",
                     coupon="0.044", freq="2", maturity="2033-05-01", price=1.0410, duration=6.4),
    "BONO_2031": dict(kind="govbond", cic="ES11", isin="ES00000128Q6", name="BONO 3.15% 30/04/2031",
                      issuer="REINO DE ESPANA", ccy="EUR", uac="1", rate_type="Fixed",
                      coupon="0.0315", freq="1", maturity="2031-04-30", price=1.0055, duration=4.9),
    "DSL_2030": dict(kind="govbond", cic="NL11", isin="NL00150016M6", name="NEDERLAND 2.00% 15/07/2030",
                     issuer="KONINKRIJK DER NEDERLANDEN", ccy="EUR", uac="1", rate_type="Fixed",
                     coupon="0.020", freq="1", maturity="2030-07-15", price=0.9720, duration=4.3),
    "RAGB_2037": dict(kind="govbond", cic="AT11", isin="AT0000A2QFE3", name="OESTERREICH 2.90% 20/02/2037",
                      issuer="REPUBLIK OESTERREICH", ccy="EUR", uac="1", rate_type="Fixed",
                      coupon="0.029", freq="1", maturity="2037-02-20", price=1.0180, duration=9.4),

    # --- corporate bonds ---------------------------------------------------
    "SAP_BOND_2029": dict(kind="corpbond", cic="DE21", isin="DE000A3H3JZ6", name="SAP SE 1.625% 10/03/2029",
                          issuer="SAP SE", ccy="EUR", uac="2", rate_type="Fixed",
                          coupon="0.01625", freq="1", maturity="2029-03-10", price=0.9560, duration=3.1,
                          callable="Cal", call_date="2028-12-10", call_dir="I", strike="1"),
    "SIE_BOND_2031": dict(kind="corpbond", cic="DE21", isin="DE000A3E5N96", name="SIEMENS AG 2.875% 22/02/2031",
                          issuer="SIEMENS AG", ccy="EUR", uac="2", rate_type="Fixed",
                          coupon="0.02875", freq="1", maturity="2031-02-22", price=1.0030, duration=4.6),
    "ALV_BOND_2033": dict(kind="corpbond", cic="DE21", isin="DE000A30VN69", name="ALLIANZ SE 4.252% 06/09/2033",
                          issuer="ALLIANZ SE", ccy="EUR", uac="2", rate_type="Fixed",
                          coupon="0.04252", freq="1", maturity="2033-09-06", price=1.0640, duration=6.1),
    "DTE_FRN_2028": dict(kind="corpbond", cic="DE21", isin="DE000A3MQS15", name="DEUTSCHE TELEKOM FRN 15/06/2028",
                         issuer="DEUTSCHE TELEKOM AG", ccy="EUR", uac="2", rate_type="Floating",
                         freq="4", maturity="2028-06-15", price=1.0005, duration=0.2,
                         index_id="EUR003M", index_src="BLOOMBERG", index_name="Euribor 3 month", margin="0.0055"),
    "BAS_BOND_2030": dict(kind="corpbond", cic="DE21", isin="DE000A3MP7L1", name="BASF SE 3.125% 05/07/2030",
                          issuer="BASF SE", ccy="EUR", uac="2", rate_type="Fixed",
                          coupon="0.03125", freq="1", maturity="2030-07-05", price=1.0090, duration=4.1),
    "TTE_BOND_2032": dict(kind="corpbond", cic="FR21", isin="FR001400H576", name="TOTALENERGIES SE 3.750% 14/09/2032",
                          issuer="TOTALENERGIES SE", ccy="EUR", uac="2", rate_type="Fixed",
                          coupon="0.0375", freq="1", maturity="2032-09-14", price=1.0350, duration=5.7),
    "SAN_BOND_2029": dict(kind="corpbond", cic="FR21", isin="FR001400BQ52", name="SANOFI SA 3.500% 21/06/2029",
                          issuer="SANOFI SA", ccy="EUR", uac="2", rate_type="Fixed",
                          coupon="0.035", freq="1", maturity="2029-06-21", price=1.0210, duration=3.2),
    "AXA_FRN_2027": dict(kind="corpbond", cic="FR21", isin="FR001400CJ35", name="AXA SA FRN 09/11/2027",
                         issuer="AXA SA", ccy="EUR", uac="2", rate_type="Floating",
                         freq="4", maturity="2027-11-09", price=0.9990, duration=0.2,
                         index_id="EUR006M", index_src="BLOOMBERG", index_name="Euribor 6 month", margin="0.0090"),
    "ENEL_BOND_2031": dict(kind="corpbond", cic="IT21", isin="IT0005539934", name="ENEL SPA 4.250% 14/06/2031",
                           issuer="ENEL SPA", ccy="EUR", uac="2", rate_type="Fixed",
                           coupon="0.0425", freq="1", maturity="2031-06-14", price=1.0470, duration=4.7),
    "IBE_BOND_2030": dict(kind="corpbond", cic="ES21", isin="ES0445064175", name="IBERDROLA SA 3.375% 12/01/2030",
                          issuer="IBERDROLA SA", ccy="EUR", uac="2", rate_type="Fixed",
                          coupon="0.03375", freq="1", maturity="2030-01-12", price=1.0140, duration=3.7),
    "AAPL_BOND_2031": dict(kind="corpbond", cic="US21", isin="US037833EK49", name="APPLE INC 4.150% 09/05/2031",
                           issuer="APPLE INC", ccy="USD", uac="2", rate_type="Fixed",
                           coupon="0.0415", freq="2", maturity="2031-05-09", price=1.0025, duration=4.5),

    # --- equities (real, publicly published ISINs) ------------------------
    "SAP": dict(kind="equity", cic="DE31", isin="DE0007164600", name="SAP SE",
                issuer="SAP SE", ccy="EUR", uac="3L", price=232.40),
    "SIE": dict(kind="equity", cic="DE31", isin="DE0007236101", name="SIEMENS AG",
                issuer="SIEMENS AG", ccy="EUR", uac="3L", price=196.80),
    "ALV": dict(kind="equity", cic="DE31", isin="DE0008404005", name="ALLIANZ SE",
                issuer="ALLIANZ SE", ccy="EUR", uac="3L", price=348.90),
    "DTE": dict(kind="equity", cic="DE31", isin="DE0005557508", name="DEUTSCHE TELEKOM AG",
                issuer="DEUTSCHE TELEKOM AG", ccy="EUR", uac="3L", price=32.15),
    "BAS": dict(kind="equity", cic="DE31", isin="DE000BASF111", name="BASF SE",
                issuer="BASF SE", ccy="EUR", uac="3L", price=44.62),
    "NESN": dict(kind="equity", cic="CH31", isin="CH0038863350", name="NESTLE SA",
                 issuer="NESTLE SA", ccy="CHF", uac="3L", price=78.24),
    "NOVN": dict(kind="equity", cic="CH31", isin="CH0012005267", name="NOVARTIS AG",
                 issuer="NOVARTIS AG", ccy="CHF", uac="3L", price=96.55),
    "ROG": dict(kind="equity", cic="CH31", isin="CH0012032048", name="ROCHE HOLDING AG",
                issuer="ROCHE HOLDING AG", ccy="CHF", uac="3L", price=272.10),
    "ASML": dict(kind="equity", cic="NL31", isin="NL0010273215", name="ASML HOLDING NV",
                 issuer="ASML HOLDING NV", ccy="EUR", uac="3L", price=678.30),
    "AIR": dict(kind="equity", cic="NL31", isin="NL0000235190", name="AIRBUS SE",
                issuer="AIRBUS SE", ccy="EUR", uac="3L", price=162.44),
    "MC": dict(kind="equity", cic="FR31", isin="FR0000121014", name="LVMH SE",
               issuer="LVMH SE", ccy="EUR", uac="3L", price=612.70),
    "TTE": dict(kind="equity", cic="FR31", isin="FR0000120271", name="TOTALENERGIES SE",
                issuer="TOTALENERGIES SE", ccy="EUR", uac="3L", price=55.18),
    "SAN": dict(kind="equity", cic="FR31", isin="FR0000120578", name="SANOFI SA",
                issuer="SANOFI SA", ccy="EUR", uac="3L", price=91.36),
    "AI": dict(kind="equity", cic="FR31", isin="FR0000120073", name="AIR LIQUIDE SA",
               issuer="AIR LIQUIDE SA", ccy="EUR", uac="3L", price=168.92),
    "ULVR": dict(kind="equity", cic="GB31", isin="GB00B10RZP78", name="UNILEVER PLC",
                 issuer="UNILEVER PLC", ccy="GBP", uac="3L", price=46.85),
    "AZN": dict(kind="equity", cic="GB31", isin="GB0009895292", name="ASTRAZENECA PLC",
                issuer="ASTRAZENECA PLC", ccy="GBP", uac="3L", price=118.40),
    "ENEL": dict(kind="equity", cic="IT31", isin="IT0003128367", name="ENEL SPA",
                 issuer="ENEL SPA", ccy="EUR", uac="3L", price=7.42),
    "IBE": dict(kind="equity", cic="ES31", isin="ES0144580Y14", name="IBERDROLA SA",
                issuer="IBERDROLA SA", ccy="EUR", uac="3L", price=14.86),
    "AAPL": dict(kind="equity", cic="US31", isin="US0378331005", name="APPLE INC",
                 issuer="APPLE INC", ccy="USD", uac="3L", price=241.55),
    "MSFT": dict(kind="equity", cic="US31", isin="US5949181045", name="MICROSOFT CORP",
                 issuer="MICROSOFT CORP", ccy="USD", uac="3L", price=428.10),
    "NVDA": dict(kind="equity", cic="US31", isin="US67066G1040", name="NVIDIA CORP",
                 issuer="NVIDIA CORP", ccy="USD", uac="3L", price=139.75),

    # --- fund units (real, publicly published ISINs) ----------------------
    "IWDA": dict(kind="fund", cic="IE41", isin="IE00B4L5Y983", name="ISHARES CORE MSCI WORLD UCITS ETF",
                 issuer="BLACKROCK ASSET MANAGEMENT IRELAND", ccy="EUR", uac="4", price=104.62),
    "CSPX": dict(kind="fund", cic="IE41", isin="IE00B5BMR087", name="ISHARES CORE SP FIVE HUNDRED UCITS ETF",
                 issuer="BLACKROCK ASSET MANAGEMENT IRELAND", ccy="USD", uac="4", price=612.35),
    "VWCE": dict(kind="fund", cic="IE41", isin="IE00B3RBWM25", name="VANGUARD FTSE ALL WORLD UCITS ETF",
                 issuer="VANGUARD GROUP IRELAND", ccy="EUR", uac="4", price=132.80),

    # --- derivatives -------------------------------------------------------
    "FGBL_MAR26": dict(kind="future", cic="XLA1", isin="DE000C6AXQV7", name="EUREX EURO BUND FUTURE MAR 2026",
                       issuer="EUREX CLEARING AG", ccy="EUR", uac="A", price=132.44,
                       contract_size="1000", expiry="2026-03-10",
                       underlying_issuer="BUNDESREPUBLIK DEUTSCHLAND",
                       underlying_cic="DE11", underlying_isin="DE000BU2Z023",
                       underlying_name="BUNDESANLEIHE 2.60% 15/08/2034", underlying_ccy="EUR",
                       underlying_price=101.25),
    "ESTX_CALL": dict(kind="option", cic="XLB1", isin="DE000C7QVJK3", name="EURO STOXX FIFTY CALL DEC 2026",
                      issuer="EUREX CLEARING AG", ccy="EUR", uac="B", price=48.20,
                      contract_size="10", expiry="2026-12-18",
                      underlying_cic="EU31", underlying_isin="EU0009658145",
                      underlying_name="EURO STOXX FIFTY INDEX", underlying_ccy="EUR",
                      underlying_issuer="STOXX LTD", underlying_price=5142.60,
                      strike="5000", exercise="EU", callput="Cal"),

    # --- cash --------------------------------------------------------------
    # Cash carries the depositary as its counterparty — field 52 (issuer
    # country) is mandatory on every row, cash lines included.
    "CASH_EUR": dict(kind="cash", cic="XL71", isin="CASH-EUR-001", name="CASH ACCOUNT EUR",
                     issuer="DEMO CUSTODIAN BANK SA", ccy="EUR", uac="7"),
    "CASH_USD": dict(kind="cash", cic="XL71", isin="CASH-USD-001", name="CASH ACCOUNT USD",
                     issuer="DEMO CUSTODIAN BANK SA", ccy="USD", uac="7"),
    "CASH_CHF": dict(kind="cash", cic="XL71", isin="CASH-CHF-001", name="CASH ACCOUNT CHF",
                     issuer="DEMO CUSTODIAN BANK SA", ccy="CHF", uac="7"),
}

# FX to the portfolio currency (EUR) as of the valuation date.
FX_TO_EUR = {"EUR": 1.0, "USD": 0.9240, "CHF": 1.0640, "GBP": 1.1830}

# --------------------------------------------------------------- funds -----
# ``book`` is (instrument key, market value in portfolio currency).
FUNDS: list[dict] = [
    dict(
        code="FR0010000001", name="Demo Euro Bond Fund", currency="EUR", country="FR",
        shares="1250000", cic="FR41", custodian="DEMO CUSTODIAN BANK SA", custodian_country="LU",
        issuer="DEMO CUSTODIAN BANK SA",
        book=[
            ("OAT_2032", 14_200_000), ("BUND_2034", 12_800_000), ("BTP_2033", 9_600_000),
            ("BONO_2031", 7_400_000), ("DSL_2030", 6_100_000), ("RAGB_2037", 4_900_000),
            ("SAP_BOND_2029", 5_300_000), ("SIE_BOND_2031", 4_700_000),
            ("ALV_BOND_2033", 4_200_000), ("DTE_FRN_2028", 3_900_000),
            ("BAS_BOND_2030", 3_600_000), ("TTE_BOND_2032", 3_400_000),
            ("SAN_BOND_2029", 3_100_000), ("AXA_FRN_2027", 2_800_000),
            ("ENEL_BOND_2031", 2_500_000), ("IBE_BOND_2030", 2_200_000),
            ("AAPL_BOND_2031", 1_900_000), ("IWDA", 1_500_000),
            ("FGBL_MAR26", 320_000), ("CASH_EUR", 2_580_000),
        ],
    ),
    dict(
        code="DE0010000002", name="Demo Global Equity Fund", currency="EUR", country="DE",
        shares="880000", cic="DE41", custodian="DEMO CUSTODIAN BANK SA", custodian_country="LU",
        issuer="DEMO CUSTODIAN BANK SA",
        book=[
            ("SAP", 6_800_000), ("SIE", 6_200_000), ("ALV", 5_400_000), ("DTE", 4_800_000),
            ("BAS", 3_900_000), ("NESN", 5_100_000), ("NOVN", 4_600_000), ("ROG", 4_100_000),
            ("ASML", 6_500_000), ("AIR", 3_700_000), ("MC", 5_800_000), ("TTE", 4_400_000),
            ("SAN", 3_500_000), ("AI", 3_200_000), ("ULVR", 2_900_000), ("AZN", 2_700_000),
            ("IWDA", 4_300_000), ("CSPX", 3_800_000), ("ESTX_CALL", 260_000),
            ("CASH_EUR", 1_940_000),
        ],
    ),
    dict(
        code="LU0010000003", name="Demo Multi Asset Fund", currency="EUR", country="LU",
        shares="640000", cic="LU41", custodian="DEMO CUSTODIAN BANK SA", custodian_country="LU",
        issuer="DEMO CUSTODIAN BANK SA",
        book=[
            ("OAT_2032", 5_600_000), ("BUND_2034", 5_100_000), ("BTP_2033", 3_800_000),
            ("SAP_BOND_2029", 2_900_000), ("DTE_FRN_2028", 2_600_000),
            ("ENEL_BOND_2031", 2_300_000), ("AAPL_BOND_2031", 2_100_000),
            ("SAP", 3_400_000), ("ASML", 3_100_000), ("MC", 2_800_000), ("NESN", 2_600_000),
            ("AAPL", 3_300_000), ("MSFT", 3_000_000), ("NVDA", 2_400_000),
            ("IWDA", 2_200_000), ("VWCE", 1_800_000),
            ("FGBL_MAR26", 180_000), ("ESTX_CALL", 140_000),
            ("CASH_EUR", 1_360_000), ("CASH_USD", 890_000),
        ],
    ),
]

# --------------------------------------------------------------- defects ---
# What the demo is meant to *show*. Addressed as (fund index, position index
# within that fund's book, field number) → replacement value; ``None`` blanks
# the cell. Fund 0 stays clean so the per-fund score table has a control.
#
# Kept to ~15 on 60 rows: enough that the findings list shows real variety,
# few enough that it still reads as "a good file with issues" rather than
# "generated garbage".
def _bump_last(code: str) -> str:
    """Corrupt exactly the check digit — the classic transcription error."""
    last = code[-1]
    return code[:-1] + (str((int(last) + 1) % 10) if last.isdigit() else "0")


# Derived, not typed, so they stay wrong-by-one even if the catalogue entry
# they corrupt is ever corrected.
BROKEN_ISIN = _bump_last(INSTRUMENTS["DTE"]["isin"])          # fund 1, position 3
BROKEN_LEI = _bump_last(ISSUERS["NESTLE SA"]["lei"])          # fund 1, position 5

CELL_DEFECTS: dict[tuple[int, int, str], str | None] = {
    # --- fund 1 (Demo Global Equity Fund): mechanical / data-entry errors --
    (1, 3, "14"): BROKEN_ISIN,       # ISIN/14   — Luhn check digit off by one
    (1, 5, "47"): BROKEN_LEI,        # LEI/47    — mod-97 check digits wrong
    (1, 7, "21"): "EURO",            # FORMAT/21 — not a 3-letter ISO 4217 code
    (1, 9, "52"): "GERMANY",         # FORMAT/52 — country name instead of alpha-2
    (1, 11, "15"): "42",             # FORMAT/15 — outside the closed list {1..9,99}
    (1, 13, "17"): None,             # PRESENCE/17 — instrument name missing
    (1, 15, "14"): None,             # PRESENCE/14 — instrument code missing
    (1, 15, "46"): None,             # COND_PRESENCE/46 — issuer name missing too
    # --- fund 2 (Demo Multi Asset Fund): cross-field inconsistencies -------
    (2, 2, "38"): "3",               # XF-08 — coupon frequency not in {0,1,2,4,12,52}
    (2, 4, "34"): None,              # XF-10 — floater without index id …
    (2, 4, "35"): None,              # …
    (2, 4, "36"): None,              # …
    (2, 4, "37"): None,              # …
    (2, 6, "39"): "2019-06-30",      # XF-11 — maturity before the reporting date
    (2, 6, "40"): "Perpetual",       # FORMAT/40 — outside {Bullet, Sinkable, defaulted}
    (2, 16, "67"): None,             # XF-14 — future without the underlying CIC
    (2, 10, "141"): None,            # XF-09 — custodian code without its code type
}

# Portfolio-level defects, applied to every row of that fund.
FUND_DEFECTS: dict[int, dict[str, str]] = {
    # Declared cash ratio 12 % against an actual ~4 % → XF-05 (tolerance is
    # ±0.05), and the weights are scaled to Σ 0.955 → XF-04. Both are the
    # classic "header figures came from a different run" bug.
    2: {"9": "0.12"},
}
WEIGHT_SCALE: dict[int, float] = {2: 0.955}

# Rows whose identifiers are deliberately broken and must be skipped by
# assert_identifiers().
_INTENTIONALLY_BROKEN = {BROKEN_ISIN, BROKEN_LEI}


# ------------------------------------------------------------- row build ---

def _num(x: float, places: int = 2) -> str:
    return f"{x:.{places}f}"


def _group_lei(issuer: dict) -> str:
    """LEI of the issuer's group — its own when the group has no separate row."""
    group = issuer["group"]
    return ISSUERS[group]["lei"] if group in ISSUERS else issuer["lei"]


def _issuer_block(row: dict, issuer_key: str) -> None:
    iss = ISSUERS[issuer_key]
    row["46"] = issuer_key
    row["47"] = iss["lei"]
    row["48"] = "1"                    # 1 = LEI → XF-20 requires 47, which we filled
    row["49"] = iss["group"]
    row["50"] = _group_lei(iss)
    row["51"] = "1"                    # XF-21 requires 50, filled above
    row["52"] = iss["country"]
    row["53"] = "1" if iss["country"] in _EEA else "3"
    row["54"] = iss["nace"]
    row["148"] = iss["nace"]
    row["55"] = "NC"
    row["137"] = iss["sector"]
    row["57"] = "Y" if iss["nace"] == "O8411" else "N"


_EEA = {"AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR",
        "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK",
        "SI", "ES", "SE", "IS", "LI", "NO"}


def _portfolio_block(fund: dict, nav: float, share_price: float, cash_ratio: float) -> dict:
    """The fields that repeat identically on every row of one fund."""
    cust = ISSUERS[fund["issuer"]]
    return {
        "1": fund["code"],
        "2": "1",
        "3": fund["name"],
        "4": fund["currency"],
        "5": _num(nav),
        "6": VALUATION_DATE,
        "7": REPORTING_DATE,
        "8": _num(share_price, 4),
        "8b": fund["shares"],
        "9": _num(cash_ratio, 6),
        "10": "4.20",
        "11": "N",                     # "Y" would make XF-01 demand fields 97..105b
        "115": cust["lei"],
        "116": "1",                    # XF-23 requires 115, filled above
        "117": fund["issuer"],
        "118": cust["nace"],
        "119": _group_lei(cust),
        "120": "1",                    # XF-24 requires 119, filled above
        "121": cust["group"],
        "122": cust["country"],
        "123": fund["cic"],
        "123a": fund["custodian_country"],
        "124": "4.20",
        "126": _num(nav * 0.0031),
        "133": fund["custodian"],
        "140": cust["lei"],
        "141": "1",                    # XF-09 wants 140 and 141 together
        "1000": TPT_VERSION,
    }


def _position_block(inst: dict, mv_pc: float, nav: float) -> dict:
    """Everything that describes one holding."""
    ccy = inst["ccy"]
    fx = FX_TO_EUR[ccy]
    mv_qc = mv_pc / fx
    kind = inst["kind"]
    is_bond = kind in ("govbond", "corpbond")
    # Bonds quote clean; the delta between dirty and clean is accrued interest.
    accrued = 0.0092 if is_bond else 0.0
    row: dict[str, str] = {
        "12": inst["cic"],
        "13": "1",
        "14": inst["isin"],
        "15": "1" if not inst["isin"].startswith("CASH") else "99",
        "17": inst["name"],
        "21": ccy,
        "22": _num(mv_qc),
        "23": _num(mv_qc * (1 - accrued)),
        "24": _num(mv_pc),
        "25": _num(mv_pc * (1 - accrued)),
        "26": _num(mv_pc / nav, 6),
        "27": _num(mv_qc),
        "28": _num(mv_pc),
        "30": _num(mv_pc / nav, 6),
        "106": "9",                    # not pledged as collateral
        "107": inst.get("depot", "LU"),
        "108": "2",                    # non-participation
        "110": "1",                    # quoted market price, active market
        "131": inst["uac"],
    }

    if kind == "cash":
        row["15"] = "99"
        row["18"] = _num(mv_qc)
        _issuer_block(row, inst["issuer"])
        row["137"] = "12"              # cash-specific ESA sector (MFI deposit)
        return row

    price = inst["price"]
    if is_bond:
        nominal_qc = mv_qc / price
        row["18"] = _num(nominal_qc)
        row["19"] = _num(nominal_qc)
        row["32"] = inst["rate_type"]
        row["38"] = inst["freq"]
        row["39"] = inst["maturity"]
        row["40"] = "Bullet"
        row["41"] = "1"
        row["90"] = _num(inst["duration"], 4)
        row["91"] = _num(inst["duration"], 4)
        row["92"] = _num(inst["duration"] * 0.98, 4)
        if inst["rate_type"] == "Fixed":
            row["33"] = inst["coupon"]
        else:
            # XF-17: field 34 filled ⇒ 35, 36, 37 must all be there too.
            row["34"] = inst["index_id"]
            row["35"] = inst["index_src"]
            row["36"] = inst["index_name"]
            row["37"] = inst["margin"]
        if inst.get("callable"):
            # XF-18/XF-19: 42 filled ⇒ 43, 44 and 45 must follow.
            row["42"] = inst["callable"]
            row["43"] = inst["call_date"]
            row["44"] = inst["call_dir"]
            row["45"] = inst["strike"]
            row["91"] = _num(inst["duration"] * 0.85, 4)
        _issuer_block(row, inst["issuer"])
        return row

    if kind in ("equity", "fund"):
        row["18"] = _num(mv_qc / price, 4)
        row["93"] = "1"
        _issuer_block(row, inst["issuer"])
        return row

    # future / option
    row["18"] = _num(mv_qc / price, 4)
    row["20"] = inst["contract_size"]
    row["39"] = inst["expiry"]
    row["65"] = "Y"                    # held for hedging
    row["67"] = inst["underlying_cic"]
    row["68"] = inst["underlying_isin"]
    row["69"] = "1"
    row["70"] = inst["underlying_name"]
    row["71"] = inst["underlying_ccy"]
    row["72"] = _num(inst["underlying_price"], 4)
    row["74"] = "1"                    # underlying quoted in the EEA
    row["93"] = "0.65" if kind == "option" else "1"
    if kind == "option":
        row["60"] = inst["callput"]
        row["61"] = inst["strike"]
        row["64"] = inst["exercise"]
    _issuer_block(row, inst["issuer"])
    # The underlying has its own issuer block (fields 80..86); XF-22 wants 84
    # whenever 85 says "LEI", so both are filled together.
    und = ISSUERS[inst["underlying_issuer"]]
    row["80"] = inst["underlying_issuer"]
    row["81"] = und["lei"]
    row["82"] = "1"
    row["83"] = und["group"]
    row["84"] = _group_lei(und)
    row["85"] = "1"
    row["86"] = und["country"]
    return row


def build_showcase() -> list[dict[str, str]]:
    """The 60 showcase rows, defects applied."""
    out: list[dict[str, str]] = []
    for fi, fund in enumerate(FUNDS):
        book = fund["book"]
        nav = float(sum(mv for _, mv in book))
        cash_mv = sum(mv for key, mv in book if INSTRUMENTS[key]["kind"] == "cash")
        share_price = nav / float(fund["shares"])
        portfolio = _portfolio_block(fund, nav, share_price, cash_mv / nav)
        portfolio.update(FUND_DEFECTS.get(fi, {}))
        scale = WEIGHT_SCALE.get(fi, 1.0)

        for pi, (key, mv) in enumerate(book):
            row = {n: "" for n in SHOWCASE_NUMS}
            row.update(portfolio)
            row.update(_position_block(INSTRUMENTS[key], float(mv), nav))
            if scale != 1.0:
                row["26"] = _num(float(row["26"]) * scale, 6)
            for (dfi, dpi, num), value in CELL_DEFECTS.items():
                if (dfi, dpi) == (fi, pi):
                    row[num] = value or ""
            out.append(row)
    return out


def _check_defects() -> None:
    """A defect addressing a column or row that does not exist does nothing —
    and a demo quietly missing a finding is the failure mode this whole file
    exists to avoid. Fail at import instead."""
    bad = []
    for fi, pi, num in CELL_DEFECTS:
        if num not in SHOWCASE_NUMS:
            bad.append(f"field {num} is not in SHOWCASE_NUMS")
        elif fi >= len(FUNDS) or pi >= len(FUNDS[fi]["book"]):
            bad.append(f"fund {fi} has no position {pi}")
    for fi in list(FUND_DEFECTS) + list(WEIGHT_SCALE):
        if fi >= len(FUNDS):
            bad.append(f"fund {fi} does not exist")
    if bad:
        raise SystemExit("tpt_showcase: unreachable defect\n  " + "\n  ".join(bad))


_check_defects()


# ------------------------------------------------------- identifier guard --

def assert_identifiers(isin_check: Callable[[str], str],
                       lei_check: Callable[[str], str]) -> None:
    """Recompute every check digit in the catalogue.

    Called by the generator so a mistyped identifier fails the build instead
    of turning into an unexplained ISIN/LEI finding in the demo. The check
    functions come from ``build_examples.py`` (the same ones the validator
    mirrors); passing them in keeps this module import-free.
    """
    bad: list[str] = []
    for key, inst in INSTRUMENTS.items():
        isin = inst["isin"]
        if isin in _INTENTIONALLY_BROKEN or isin.startswith("CASH"):
            continue
        if len(isin) == 12 and isin_check(isin[:11]) != isin[11]:
            bad.append(f"{key}: ISIN {isin} fails Luhn "
                       f"(expected check digit {isin_check(isin[:11])})")
        und = inst.get("underlying_isin")
        if und and len(und) == 12 and isin_check(und[:11]) != und[11]:
            bad.append(f"{key}: underlying ISIN {und} fails Luhn "
                       f"(expected check digit {isin_check(und[:11])})")
    for name, iss in ISSUERS.items():
        lei = iss["lei"]
        if lei in _INTENTIONALLY_BROKEN:
            continue
        if len(lei) != 20 or lei_check(lei[:18]) != lei[18:]:
            bad.append(f"{name}: LEI {lei} fails mod-97 "
                       f"(expected check digits {lei_check(lei[:18])})")
    if bad:
        raise SystemExit("tpt_showcase: invalid identifiers\n  " + "\n  ".join(bad))
