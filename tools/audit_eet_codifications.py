"""Distinct codifications + frequency for EET V1.1.2 and V1.1.3.

Highlights the multi-value forms (``A / A;D / B;D;F`` etc.) and the
whitespace variants of common codifications so the executor can confirm
``CodificationParser`` covers them. Pure read-only.
"""
from __future__ import annotations

from collections import Counter

from findatex_sample_helpers import load_spec


def collect(version: str) -> Counter[str]:
    _, fields = load_spec("EET", version)
    return Counter((f.codif or "").strip() for f in fields if f.codif)


def main() -> int:
    for version in ("V1.1.2", "V1.1.3"):
        counts = collect(version)
        print(f"## EET {version} — {len(counts)} distinct codifications")
        print()
        print("| count | codification |")
        print("|------:|:-------------|")
        for codif, n in counts.most_common():
            short = codif.replace("\n", " ⏎ ")
            if len(short) > 120:
                short = short[:117] + "..."
            print(f"| {n} | `{short}` |")
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
