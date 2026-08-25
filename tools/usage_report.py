#!/usr/bin/env python3
"""Print an aggregate usage overview from the production Postgres (Hetzner VPS tanzapp-prod).

Reads the `usage_event` table (anonymous opt-out telemetry — see
docs/USAGE_STATS.md) and prints a compact, read-only summary: totals, per
template/version, source/mode, daily volume, countries, top rule hits and DB
size. Issues **no DDL or writes**.

Connection (all env-overridable; defaults match the Cloud Run deploy):

    FINDATEX_WEB_USAGE_DB_HOST  (default: the Hetzner VPS IP)
    FINDATEX_WEB_USAGE_DB_NAME  (default: findatex_stats)
    FINDATEX_WEB_USAGE_DB_USER  (default: findatex)

Password resolution order:
    1. $FINDATEX_WEB_USAGE_DB_PASSWORD  (or $PGPASSWORD)
    2. gcloud secret  findatex-usage-db-password  (needs `gcloud auth login`)

Requires: psycopg  ( pip install --user "psycopg[binary]" )

Usage:
    python3 tools/usage_report.py            # full overview
    python3 tools/usage_report.py --days 30  # restrict to the last N days
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys

try:
    import psycopg
except ImportError:
    sys.exit('psycopg not installed. Run:  pip install --user "psycopg[binary]"')

DEFAULT_HOST = "62.238.116.11"
DEFAULT_NAME = "findatex_stats"
DEFAULT_USER = "findatex"
PW_SECRET = "findatex-usage-db-password"


def resolve_password() -> str:
    pw = os.environ.get("FINDATEX_WEB_USAGE_DB_PASSWORD") or os.environ.get("PGPASSWORD")
    if pw:
        return pw
    try:
        out = subprocess.run(
            ["gcloud", "secrets", "versions", "access", "latest", f"--secret={PW_SECRET}"],
            check=True, capture_output=True, text=True,
        )
        return out.stdout.strip("\n")
    except (subprocess.CalledProcessError, FileNotFoundError) as exc:
        detail = getattr(exc, "stderr", "") or str(exc)
        sys.exit(
            "No DB password. Set $FINDATEX_WEB_USAGE_DB_PASSWORD or authenticate "
            f"gcloud (gcloud auth login).\n{detail}"
        )


def main() -> None:
    ap = argparse.ArgumentParser(description="FinDatEx Validator usage overview (read-only).")
    ap.add_argument("--days", type=int, default=None,
                    help="restrict to runs received within the last N days")
    args = ap.parse_args()

    where = ""
    if args.days:
        where = f"WHERE received_at > now() - interval '{int(args.days)} days'"
    # AND-variant for queries that already carry a WHERE clause.
    and_clause = where.replace("WHERE", "AND", 1) if where else ""

    dsn = (
        f"host={os.environ.get('FINDATEX_WEB_USAGE_DB_HOST', DEFAULT_HOST)} "
        f"dbname={os.environ.get('FINDATEX_WEB_USAGE_DB_NAME', DEFAULT_NAME)} "
        f"user={os.environ.get('FINDATEX_WEB_USAGE_DB_USER', DEFAULT_USER)} "
        f"password={resolve_password()} sslmode=require"
    )

    scope = f"letzte {args.days} Tage" if args.days else "gesamter Zeitraum"
    print(f"FinDatEx Validator — Nutzungsübersicht ({scope})")

    queries = [
        ("Gesamt / Zeitraum", f"""
            SELECT count(*) runs, count(DISTINCT install_id) installs,
                   min(received_at)::date first_run, max(received_at)::date last_run
            FROM usage_event {where};"""),
        ("Quelle & Modus", f"""
            SELECT source, mode, count(*) runs, round(avg(duration_ms)) avg_ms
            FROM usage_event {where} GROUP BY 1,2 ORDER BY 1,2;"""),
        ("Pro Template/Version", f"""
            SELECT template_id, template_version, count(*) runs,
                   round(avg(overall_score),1) avg_score, round(avg(error_count),2) avg_err
            FROM usage_event {where} GROUP BY 1,2 ORDER BY 3 DESC;"""),
        ("Läufe pro Tag (max. 14)", f"""
            SELECT received_at::date AS d, count(*) runs
            FROM usage_event {where} GROUP BY 1 ORDER BY 1 DESC LIMIT 14;"""),
        ("Länder", f"""
            SELECT coalesce(country_code,'(unbekannt)') cc, count(*) runs
            FROM usage_event {where} GROUP BY 1 ORDER BY 2 DESC LIMIT 15;"""),
        ("Top Regel-Treffer", f"""
            SELECT r rule_id, count(*) hits
            FROM usage_event, unnest(rule_ids) r {where} GROUP BY 1 ORDER BY 2 DESC LIMIT 15;"""),
        ("App-Version / OS / external", f"""
            SELECT coalesce(app_version,'?') ver, coalesce(os_name,'?') os,
                   bool_or(external_enabled) ext_any, count(*)
            FROM usage_event {where} GROUP BY 1,2 ORDER BY 4 DESC LIMIT 10;"""),
        ("Findings-Summen", f"""
            SELECT sum(file_count) files, sum(row_count) rows_total,
                   sum(error_count) err, sum(warning_count) warn, sum(info_count) info
            FROM usage_event {where};"""),
        ("DB-Größe (free-tier ≤ 500 MB)", f"""
            SELECT pg_size_pretty(pg_database_size(current_database())) db_size, count(*) rows
            FROM usage_event {where};"""),
    ]

    with psycopg.connect(dsn, connect_timeout=20) as conn:
        for title, sql in queries:
            print(f"\n### {title}")
            with conn.cursor() as cur:
                cur.execute(sql)
                cols = [d.name for d in cur.description]
                rows = cur.fetchall()
                widths = [len(c) for c in cols]
                for r in rows:
                    for i, v in enumerate(r):
                        widths[i] = max(widths[i], len("" if v is None else str(v)))
                print("  ".join(c.ljust(widths[i]) for i, c in enumerate(cols)))
                for r in rows:
                    print("  ".join(
                        ("" if v is None else str(v)).ljust(widths[i]) for i, v in enumerate(r)))
                if not rows:
                    print("(keine Zeilen)")


if __name__ == "__main__":
    main()
