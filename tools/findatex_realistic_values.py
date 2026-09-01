"""Realistic per-share-class data for the EET / EMT / EPT showcase fixtures.

The numbered fixtures under ``samples/{eet,emt,ept}/`` are built by filling
every mandatory field from :func:`findatex_sample_helpers.value_for`, a
heuristic that reads the codification text and returns *something the
validator accepts*. That is exactly right for a regression fixture and
exactly wrong for a demo: every name comes out as ``Sample``, every ISIN as
``DE0007164600`` and every proportion as ``0.5``.

This module supplies the other half — a fund range that reads like a real
delivery — while leaving ``value_for`` as the fallback for every field nobody
has curated yet. Two pieces:

* :data:`SHARE_CLASSES` — 25 share classes across four products, differing in
  currency, distribution policy, SFDR article, risk and cost level, the way a
  real manufacturer's range does.
* ``OVERRIDES_*`` — ``num → value`` maps, or ``num → f(share_class)`` for
  anything that varies per row. Applied *after* ``value_for``, so an
  un-curated field still lands on a format-valid value.

EET / EMT / EPT are per-share-class templates: one row is one ISIN, not one
holding. "25 rows" therefore means a fund range, which is why the products
below carry several classes each.
"""
from __future__ import annotations

from typing import Any, Callable

# --------------------------------------------------------------- issuer ----
MANUFACTURER = "Demo Asset Management SA"
MANUFACTURER_LEI = "529900DEMOAMSA4RQ153"   # constructed, mod-97 valid
MANUFACTURER_COUNTRY = "LU"
REFERENCE_DATE = "2025-12-31"
GENERATION_TIMESTAMP = "2026-01-15 08:30:00"

# --------------------------------------------------------------- products --
# sfdr: SFDR product type as the EET codes it (NUM 27): 0 = out of scope,
# 6 = Article 6, 8 = Article 8, 9 = Article 9.
PRODUCTS: dict[str, dict[str, Any]] = {
    "GEQ": dict(
        name="Demo Global Equity Fund", sfdr="8", pai=True,
        # Art-8: minimum share of sustainable investments, attributed to the
        # environmental sub-category (NUM 42/43) rather than the social one.
        art8_min_e_s="0.35", art8_sustainable="Y", art8_min_si="0.20",
        art8_taxonomy="N", art8_environmental="Y", art8_social="N",
        sri="5", risk_profile="growth", ongoing_cost="0.0165",
        transaction_cost="0.0022", entry_cost="0.03", exit_cost="0.0",
        holding_period="5",
    ),
    "EBD": dict(
        name="Demo Euro Bond Fund", sfdr="6", pai=False,
        sri="2", risk_profile="income", ongoing_cost="0.0072",
        transaction_cost="0.0008", entry_cost="0.02", exit_cost="0.0",
        holding_period="3",
    ),
    "SMA": dict(
        name="Demo Sustainable Multi Asset Fund", sfdr="9", pai=True,
        art9_min_si="0.85", art9_min_env="0.45", art9_env_taxonomy="Y",
        art9_env_other="Y", art9_min_soc="0.25",
        paris_aligned="N", decarbonisation="N",
        sri="4", risk_profile="balanced", ongoing_cost="0.0138",
        transaction_cost="0.0017", entry_cost="0.03", exit_cost="0.0",
        holding_period="5",
    ),
    "MMF": dict(
        name="Demo Money Market Fund", sfdr="0", pai=False,
        # Out of scope: NUM 28 (the "would be eligible as" field) has to say
        # what the product would be, and every Art-8/9 field must stay empty.
        sfdr_eligible="0",
        sri="1", risk_profile="capital preservation", ongoing_cost="0.0018",
        transaction_cost="0.0001", entry_cost="0.0", exit_cost="0.0",
        holding_period="1",
    ),
}

# (product, class suffix, currency, distribution, ISIN body without check digit)
_RANGE: list[tuple[str, str, str, str, str]] = [
    ("GEQ", "A EUR ACC", "EUR", "acc",  "LU250100001"),
    ("GEQ", "A EUR DIS", "EUR", "dist", "LU250100002"),
    ("GEQ", "I EUR ACC", "EUR", "acc",  "LU250100003"),
    ("GEQ", "R EUR ACC", "EUR", "acc",  "LU250100004"),
    ("GEQ", "A USD ACC", "USD", "acc",  "LU250100005"),
    ("GEQ", "I CHF ACC", "CHF", "acc",  "LU250100006"),
    ("GEQ", "A GBP DIS", "GBP", "dist", "LU250100007"),
    ("EBD", "A EUR ACC", "EUR", "acc",  "LU250200001"),
    ("EBD", "A EUR DIS", "EUR", "dist", "LU250200002"),
    ("EBD", "I EUR ACC", "EUR", "acc",  "LU250200003"),
    ("EBD", "I EUR DIS", "EUR", "dist", "LU250200004"),
    ("EBD", "R EUR ACC", "EUR", "acc",  "LU250200005"),
    ("EBD", "A CHF ACC", "CHF", "acc",  "LU250200006"),
    ("SMA", "A EUR ACC", "EUR", "acc",  "LU250300001"),
    ("SMA", "A EUR DIS", "EUR", "dist", "LU250300002"),
    ("SMA", "I EUR ACC", "EUR", "acc",  "LU250300003"),
    ("SMA", "R EUR ACC", "EUR", "acc",  "LU250300004"),
    ("SMA", "A USD ACC", "USD", "acc",  "LU250300005"),
    ("SMA", "I GBP ACC", "GBP", "acc",  "LU250300006"),
    ("MMF", "A EUR ACC", "EUR", "acc",  "LU250400001"),
    ("MMF", "I EUR ACC", "EUR", "acc",  "LU250400002"),
    ("MMF", "I EUR DIS", "EUR", "dist", "LU250400003"),
    ("MMF", "A USD ACC", "USD", "acc",  "LU250400004"),
    ("MMF", "A CHF ACC", "CHF", "acc",  "LU250400005"),
    ("MMF", "R EUR ACC", "EUR", "acc",  "LU250400006"),
]


def share_classes(make_isin: Callable[[str], str]) -> list[dict[str, Any]]:
    """The 25 share classes, ISIN check digits computed by the caller's Luhn.

    ``make_isin`` is passed in rather than imported so this module stays free
    of cross-generator imports — the same arrangement ``tpt_showcase`` uses.
    """
    out: list[dict[str, Any]] = []
    for product_key, suffix, ccy, dist, body in _RANGE:
        product = PRODUCTS[product_key]
        out.append({
            **product,
            "product": product_key,
            "isin": make_isin(body),
            "class_name": f"{product['name']} {suffix}",
            "currency": ccy,
            "distribution": dist,
            # Institutional classes are cheaper and carry a higher minimum.
            "ongoing_cost": (_scaled(product["ongoing_cost"], 0.55)
                             if suffix.startswith("I") else product["ongoing_cost"]),
            "entry_cost": "0.0" if suffix.startswith("I") else product["entry_cost"],
        })
    return out


def _scaled(value: str, factor: float) -> str:
    return f"{float(value) * factor:.4f}"


# ------------------------------------------------------------- overrides ---
# num → literal, or num → callable(share_class). Anything absent falls through
# to findatex_sample_helpers.value_for.

OVERRIDES_EET: dict[str, Any] = {
    "5": GENERATION_TIMESTAMP,
    # File-level reporting flags. 8 = "N" keeps SFDR_ENTITY out: the
    # entity-level block is 116 mandatory fields and in practice a separate
    # delivery. 10 = "N" likewise for the IDD branch.
    "6": "Y", "7": "Y", "8": "N", "9": "Y", "10": "N",
    "11": MANUFACTURER,
    "12": "L",                                    # code type: LEI
    "13": MANUFACTURER_LEI,
    "15": REFERENCE_DATE,
    "23": lambda sc: sc["isin"],
    "24": "1",                                    # ISO 6166
    "25": lambda sc: sc["class_name"],
    "26": lambda sc: sc["currency"],
    "27": lambda sc: sc["sfdr"],
    "28": lambda sc: sc.get("sfdr_eligible", ""),
    "33": lambda sc: "Y" if sc["pai"] else "N",
    "35": lambda sc: (f"https://demo-am.example/pcdfp/{sc['isin']}/EN.pdf"
                      if sc["sfdr"] in ("8", "9") else ""),
    "36": lambda sc: "2025-11-28" if sc["sfdr"] in ("8", "9") else "",
    "82": "Y",
    "581": lambda sc: "Y" if sc["sfdr"] in ("8", "9") else "N",
    # SFDR Article 8 block — empty unless the product is Art-8.
    "30": lambda sc: sc.get("art8_min_e_s", "") if sc["sfdr"] == "8" else "",
    "40": lambda sc: sc.get("art8_sustainable", "") if sc["sfdr"] == "8" else "",
    "41": lambda sc: sc.get("art8_min_si", "") if sc["sfdr"] == "8" else "",
    "42": lambda sc: sc.get("art8_taxonomy", "") if sc["sfdr"] == "8" else "",
    "43": lambda sc: sc.get("art8_environmental", "") if sc["sfdr"] == "8" else "",
    "44": lambda sc: sc.get("art8_social", "") if sc["sfdr"] == "8" else "",
    # SFDR Article 9 block — empty unless the product is Art-9.
    "31": lambda sc: sc.get("art9_min_si", "") if sc["sfdr"] == "9" else "",
    "45": lambda sc: sc.get("art9_min_env", "") if sc["sfdr"] == "9" else "",
    "46": lambda sc: sc.get("art9_env_taxonomy", "") if sc["sfdr"] == "9" else "",
    "47": lambda sc: sc.get("art9_env_other", "") if sc["sfdr"] == "9" else "",
    "48": lambda sc: sc.get("art9_min_soc", "") if sc["sfdr"] == "9" else "",
    "80": lambda sc: sc.get("decarbonisation", "") if sc["sfdr"] == "9" else "",
    "81": lambda sc: sc.get("paris_aligned", "") if sc["sfdr"] == "9" else "",
    # PAI snapshot header — only meaningful when the product considers PAI.
    "103": lambda sc: "Q" if sc["pai"] else "",
    "104": lambda sc: REFERENCE_DATE if sc["pai"] else "",
}

OVERRIDES_EMT: dict[str, Any] = {
    "5": GENERATION_TIMESTAMP,
    "6": "Y", "7": "Y", "8": "Y",              # target market + ex-ante + ex-post
    "9": lambda sc: sc["isin"],
    "10": "1",                                  # ISO 6166
    "11": lambda sc: sc["class_name"],
    "12": lambda sc: sc["currency"],
    "13": "N",                                  # no performance fee
    "14": lambda sc: "Y" if sc["distribution"] == "dist" else "N",
    "15": REFERENCE_DATE,
    "16": "U",                                  # UCITS
    "19": MANUFACTURER,
    # --- MiFID II target market -------------------------------------------
    "31": REFERENCE_DATE,
    "32": "Y", "33": "Y", "34": "Y",            # retail / professional / eligible cpty
    "35": lambda sc: "Y" if sc["sri"] <= "2" else "N",   # basic investor
    "36": "Y",                                  # informed investor
    "37": "Y",                                  # advanced investor
    "39": lambda sc: "Y" if sc["risk_profile"] == "capital preservation" else "N",
    "42": "Y",                                  # no capital guarantee needed
    "43": "N",                                  # cannot bear loss beyond capital
    "49": lambda sc: "Y" if sc["risk_profile"] == "capital preservation" else "N",
    "50": lambda sc: "Y" if sc["risk_profile"] in ("growth", "balanced") else "N",
    "51": lambda sc: "Y" if sc["risk_profile"] in ("income", "balanced") else "N",
    "55": lambda sc: sc["holding_period"],
    "56": "Y",
    # --- costs -------------------------------------------------------------
    "71": lambda sc: sc["ongoing_cost"],
    "73": lambda sc: _scaled(sc["ongoing_cost"], 0.75),
    "75": lambda sc: sc["transaction_cost"],
    "76": "0.0",
    "79": REFERENCE_DATE,
    "84": lambda sc: sc["ongoing_cost"],
    "87": lambda sc: _scaled(sc["ongoing_cost"], 0.75),
    "89": lambda sc: sc["transaction_cost"],
    "90": "0.0",
    "91": "2025-01-01",
    "92": REFERENCE_DATE,
}

OVERRIDES_EPT: dict[str, Any] = {
    "4": GENERATION_TIMESTAMP,
    "5": "Y", "6": "Y", "7": "N", "8": "N",     # narratives + costs, no German MOPs
    "9": MANUFACTURER,
    "10": "Demo Asset Management Group",
    "14": lambda sc: sc["isin"],
    "15": "1",                                  # ISO 6166
    "16": lambda sc: sc["class_name"],
    "17": lambda sc: sc["currency"],
    "18": "2025-11-28",                         # PRIIPs KID publication date
    "19": "2",                                  # PRIIPs category 2
    "21": "N",                                  # not autocallable
    # --- risk --------------------------------------------------------------
    "30": "N",
    "31": lambda sc: sc["sri"],
    "32": "N",
    "33": lambda sc: sc["sri"],
    "34": "1",
    "35": lambda sc: sc["holding_period"],
    "36": "N",
    "38": "L",                                  # low liquidity risk
    # --- performance scenarios (net returns over the RHP) ------------------
    "41": lambda sc: _scenario(sc, "unfavourable"),
    "42": "N",
    "46": lambda sc: _scenario(sc, "moderate"),
    "47": "N",
    "51": lambda sc: _scenario(sc, "favourable"),
    "52": "N",
    "56": lambda sc: _scenario(sc, "stress"),
    "57": "N",
    "65": "Y",
    "69": "10000",
    # --- costs -------------------------------------------------------------
    "70": lambda sc: sc["entry_cost"],
    "71": "0.0",
    "72": "0.0",
    "74": "N",
    "75": lambda sc: sc["ongoing_cost"],
    "76": lambda sc: sc["transaction_cost"],
    "77": "N",
    "99": "N",
    # --- narratives --------------------------------------------------------
    "79": "N",
    "80": lambda sc: (f"Retail investors with a {sc['holding_period']} year horizon "
                      f"seeking {sc['risk_profile']} who can bear losses up to the "
                      f"amount invested."),
    "81": lambda sc: (f"The fund seeks {sc['risk_profile']} by investing in a "
                      f"diversified portfolio, denominated in {sc['currency']}."),
    "83": "Liquidity of the underlying markets may be reduced in stressed conditions.",
    "84": lambda sc: sc["name"],
    "85": "N",
    "97": "No exit cost is charged.",
    "98": "Management and administration fees are accrued daily.",
}


# Performance scenarios scale with the risk indicator, so a money-market class
# and an equity class do not end up with the same numbers.
_SCENARIO_SPREAD = {"unfavourable": -1.0, "moderate": 0.45, "favourable": 1.6,
                    "stress": -1.55}


def _scenario(share_class: dict[str, Any], which: str) -> str:
    risk = int(share_class["sri"])
    return f"{_SCENARIO_SPREAD[which] * risk * 0.045:+.4f}"


def apply_overrides(row: dict[str, str], overrides: dict[str, Any],
                    share_class: dict[str, Any]) -> dict[str, str]:
    """Overlay the curated values onto a ``value_for``-populated row.

    Only touches columns the row already has, so an override for a field that
    is not in the header set is a no-op rather than a stray column.
    """
    for num, value in overrides.items():
        if num not in row:
            continue
        row[num] = value(share_class) if callable(value) else value
    return row
