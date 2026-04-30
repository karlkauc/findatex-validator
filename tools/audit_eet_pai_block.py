"""Enumerate the PAI indicator block (NUMs whose path matches
``30\\d{3}_..._Considered_In_The_Investment_Strategy`` plus the snapshot
metadata 30000_/30010_) for EET V1.1.2 and V1.1.3.

Output is a copy-pasteable ``List.of(...)`` per version, intended for
``EetRuleSet.PAI_BLOCK_V112`` / ``PAI_BLOCK_V113``. Pure read-only.
"""
from __future__ import annotations

import re

from findatex_sample_helpers import load_spec

# Snapshot metadata that is part of the PAI block but uses a different
# path suffix; harvested separately so the regex stays focused on the
# bulk of the indicators.
META_PATH_RE = re.compile(r"^300[01]\d_.*", re.IGNORECASE)
INDICATOR_PATH_RE = re.compile(
    r"^30\d{3}_.*Considered_In_The_Investment_Strategy.*", re.IGNORECASE
)


def block_for(version: str) -> list[str]:
    _, fields = load_spec("EET", version)
    nums: list[str] = []
    for f in fields:
        if META_PATH_RE.match(f.path) or INDICATOR_PATH_RE.match(f.path):
            nums.append(f.num)
    return nums


def render(name: str, nums: list[str]) -> str:
    rows = []
    for i in range(0, len(nums), 8):
        chunk = ", ".join(f'"{n}"' for n in nums[i : i + 8])
        rows.append("    " + chunk + ("," if i + 8 < len(nums) else ""))
    body = "\n".join(rows)
    return (
        f"private static final List<String> {name} = List.of(\n"
        f"{body});"
    )


def main() -> int:
    for version, const_name in (("V1.1.2", "PAI_BLOCK_V112"),
                                ("V1.1.3", "PAI_BLOCK_V113")):
        nums = block_for(version)
        print(f"// {version} — {len(nums)} fields")
        print(render(const_name, nums))
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
