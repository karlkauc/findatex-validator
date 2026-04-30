"""Generate the EET V1.1.3 sample fixtures into ``samples/eet/``.

Five files: clean baseline plus four broken variants targeting different
rule families (presence, format, SFDR Art-8 conditional, SFDR Art-9
conditional).
"""
from __future__ import annotations

from copy import deepcopy
from pathlib import Path

from findatex_sample_helpers import (
    ROOT, FieldRow, load_spec, value_for, write_scenarios,
)

OUT = ROOT / "samples" / "eet"
OUT.mkdir(parents=True, exist_ok=True)

MANIFEST, FIELDS = load_spec("EET", "V1.1.3")

# PAI block (mirror of EetRuleSet.PAI_BLOCK) — needs to be in the header
# row so the s07 scenario can leave individual cells blank to trigger
# EET-XF-PAI-* errors.
PAI_BLOCK = [
    "103", "104",
    "106", "110", "114", "118", "122", "126", "130", "134", "138",
    "142", "146", "150", "154", "158", "162", "166", "170", "174",
    "178", "182", "186", "190", "194", "198", "202",
]

# Header row = all M-flagged fields, plus the SFDR-conditional fields
# (NUM 28, 30, 31, 40, 41, 42, 43, 44, 45, 46, 47, 48), the PAI gating
# field (33) and the PAI block, so every XF scenario has the columns it
# needs.
M_NUMS = [r.num for r in FIELDS if r.flag == "M"]
EXTRA_NUMS = (
    ["28", "30", "31", "33", "40", "41", "42", "43", "44", "45", "46", "47", "48"]
    + PAI_BLOCK
)
HEADER_NUMS = M_NUMS + [n for n in EXTRA_NUMS if n not in M_NUMS]

BY_NUM: dict[str, FieldRow] = {r.num: r for r in FIELDS}
HEADERS = [BY_NUM[n].path for n in HEADER_NUMS]


def clean_row() -> dict[str, str]:
    """Build one fully-populated EET row (M fields + SFDR Art-8 defaults)."""
    row = {n: "" for n in HEADER_NUMS}
    for n in M_NUMS:
        row[n] = value_for(BY_NUM[n].codif)
    # Make the row look like an Art-8 product so default Art-8 conditional
    # presence is satisfied — set NUM=27 to "8" and supply NUM=30/40/41/44
    # (44=Y attributes the Art-8 minimum to the social sub-category to keep
    # EET-XF-ART8-MIN-SI-SPLIT silent).
    row["27"] = "8"
    row["30"] = "0.5"
    row["40"] = "Y"
    row["41"] = "0.3"
    row["44"] = "Y"
    # NUM=33 is the PAI gating field. value_for("Y / N") returns "Y", which
    # would activate EET-XF-PAI-* against every NUM in the PAI block —
    # poisoning the "clean" baseline. Pin it to "N" so the clean file does
    # not need to populate 27 PAI indicators.
    row["33"] = "N"
    return row


# ----- scenarios ----------------------------------------------------------

def s01_clean() -> list[dict[str, str]]:
    return [clean_row()]


def s02_missing_mandatory() -> list[dict[str, str]]:
    r = clean_row()
    # Blank five M fields so PRESENCE/* fires multiple times.
    for n in ["5", "7", "23", "25", "26"]:
        r[n] = ""
    return [r]


def s03_bad_formats() -> list[dict[str, str]]:
    r = clean_row()
    r["5"] = "31/12/2025 12:00"   # NUM=5 is ISO 8601 datetime → wrong format
    r["15"] = "2025-13-40"        # NUM=15 is ISO 8601 date → impossible date
    r["26"] = "EuroX"             # NUM=26 is ISO 4217 → not a 3-letter code
    r["7"] = "Maybe"              # NUM=7 is Y/N → invalid
    return [r]


def s04_sfdr_art8_no_min() -> list[dict[str, str]]:
    r = clean_row()
    r["27"] = "8"  # SFDR Art-8 product
    r["30"] = ""   # blank → triggers EET-XF-ART8-MIN-LT
    r["40"] = "Y"  # has sustainable investments
    r["41"] = ""   # blank → triggers EET-XF-ART8-MIN-SI
    return [r]


def s05_sfdr_art9_no_min() -> list[dict[str, str]]:
    r = clean_row()
    r["27"] = "9"  # SFDR Art-9 product
    r["31"] = ""   # blank → triggers EET-XF-ART9-MIN-LT
    r["45"] = ""   # blank → triggers EET-XF-ART9-MIN-ENV
    r["48"] = ""   # blank → triggers EET-XF-ART9-MIN-SOC
    # Switch the row out of Art-8: clear the Art-8 fields so we don't double up.
    r["40"] = "N"
    r["41"] = ""
    r["30"] = ""
    return [r]


def s06_sfdr_out_of_scope_with_art_fields() -> list[dict[str, str]]:
    r = clean_row()
    # Out-of-scope product but Art-8/Art-9 fields populated — should fire
    # EET-XF-ART30-MUST-BE-ABSENT, EET-XF-ART41-MUST-BE-ABSENT,
    # EET-XF-ART45-MUST-BE-ABSENT (and similar for 31/40/42-44/46-48 if present).
    r["27"] = "0"
    r["28"] = "0"
    r["30"] = "0.5"
    r["31"] = "0.4"
    r["40"] = "Y"
    r["41"] = "0.3"
    r["45"] = "0.2"
    r["48"] = "0.1"
    return [r]


def s07_pai_yes_block_missing() -> list[dict[str, str]]:
    r = clean_row()
    # Product considers PAI but the PAI block is empty — fires EET-XF-PAI-*
    # for every NUM in the block (27 errors).
    r["33"] = "Y"
    for n in PAI_BLOCK:
        r[n] = ""
    return [r]


def s08_taxonomy_min_unattributed() -> list[dict[str, str]]:
    r = clean_row()
    # Art-8 product reporting a minimum proportion (NUM=41) but with none of
    # the sub-attribution flags 42/43/44 set — fires EET-XF-ART8-MIN-SI-SPLIT.
    r["27"] = "8"
    r["40"] = "Y"
    r["41"] = "0.3"
    for n in ["42", "43", "44"]:
        r[n] = ""
    return [r]


SCENARIOS = [
    ("01_clean.xlsx",                s01_clean),
    ("02_missing_mandatory.xlsx",    s02_missing_mandatory),
    ("03_bad_formats.xlsx",          s03_bad_formats),
    ("04_sfdr_art8_no_min.xlsx",     s04_sfdr_art8_no_min),
    ("05_sfdr_art9_no_min.xlsx",     s05_sfdr_art9_no_min),
    ("06_sfdr_out_of_scope_with_art_fields.xlsx", s06_sfdr_out_of_scope_with_art_fields),
    ("07_pai_yes_block_missing.xlsx",             s07_pai_yes_block_missing),
    ("08_taxonomy_min_unattributed.xlsx",         s08_taxonomy_min_unattributed),
]


def write_readme() -> None:
    txt = (
        "# EET V1.1.3 example files\n\n"
        "Generated by `tools/build_eet_samples.py`. One clean reference plus\n"
        "seven deliberately broken files exercising the EET rule set.\n\n"
        "| File | What it demonstrates |\n"
        "|------|----------------------|\n"
        "| `01_clean.xlsx` | All M-flagged fields populated; SFDR Art-8 product layout. |\n"
        "| `02_missing_mandatory.xlsx` | Blanks NUM=5, 7, 23, 25, 26 → PRESENCE/* errors. |\n"
        "| `03_bad_formats.xlsx` | Invalid ISO 8601 datetime / date, non-ISO-4217 currency, non-Y/N reporting flag. |\n"
        "| `04_sfdr_art8_no_min.xlsx` | SFDR Art-8 product without Art-8 minimum proportions → `EET-XF-ART8-MIN-LT`, `EET-XF-ART8-MIN-SI`. |\n"
        "| `05_sfdr_art9_no_min.xlsx` | SFDR Art-9 product without Art-9 minimum proportions → `EET-XF-ART9-MIN-LT`, `EET-XF-ART9-MIN-ENV`, `EET-XF-ART9-MIN-SOC`. |\n"
        "| `06_sfdr_out_of_scope_with_art_fields.xlsx` | NUM=27=\"0\" (out-of-scope) but Art-8/Art-9 fields populated → `EET-XF-ART30-MUST-BE-ABSENT`, `EET-XF-ART31-MUST-BE-ABSENT`, … (WARNING). |\n"
        "| `07_pai_yes_block_missing.xlsx` | NUM=33=\"Y\" (product considers PAI) but the PAI block (NUMs 103/104/106/…/202) is empty → 27× `EET-XF-PAI-*` (ERROR). |\n"
        "| `08_taxonomy_min_unattributed.xlsx` | NUM=41 reports an Art-8 minimum but NUMs 42/43/44 are blank → `EET-XF-ART8-MIN-SI-SPLIT` (WARNING). |\n\n"
        "Open via `mvn javafx:run` → EET tab → *Browse…* or run `mvn -Dtest=EetExampleSamplesTest test`.\n"
    )
    (OUT / "README.md").write_text(txt, encoding="utf-8")
    print(f"Wrote {(OUT / 'README.md').relative_to(ROOT)}")


def main() -> int:
    write_scenarios(OUT, MANIFEST["sheetName"], HEADER_NUMS, HEADERS, SCENARIOS)
    write_readme()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
