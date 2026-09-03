#!/usr/bin/env python3
"""Print a read-only usage overview of the FinDatEx Validator from the stats Postgres.

Reads `usage_event` (all event types since 2026-09) and `quick_feedback` (docs/USAGE_STATS.md,
docs/QUICK_FEEDBACK.md) on the Hetzner VPS and prints aggregates: runs per day,
desktop vs web, active installs, templates/versions, profiles, scores, error
rates, durations, most-triggered rules, countries, OS/app versions, visitors vs.
validations (the conversion funnel), traffic sources, star ratings, DB size.
No writes.

Connection (env-overridable; defaults match the Cloud Run deploy):
    USAGE_DB_HOST  (default 62.238.116.11)
    USAGE_DB_NAME  (default findatex_stats)
    USAGE_DB_USER  (default findatex)
Password: $USAGE_DB_PASSWORD or $PGPASSWORD, else
    gcloud secrets versions access latest --secret=findatex-usage-db-password --project findatex-validator

Requires: pip install --user "psycopg[binary]"

Usage:
    python3 tools/usage_report.py             # everything
    python3 tools/usage_report.py --days 30   # last N days
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
GCP_PROJECT = "findatex-validator"


def resolve_password() -> str:
    pw = os.environ.get("USAGE_DB_PASSWORD") or os.environ.get("PGPASSWORD")
    if pw:
        return pw
    try:
        out = subprocess.run(
            ["gcloud", "secrets", "versions", "access", "latest",
             f"--secret={PW_SECRET}", f"--project={GCP_PROJECT}"],
            check=True, capture_output=True, text=True,
        )
        return out.stdout.strip("\n")
    except (subprocess.CalledProcessError, FileNotFoundError) as exc:
        detail = getattr(exc, "stderr", "") or str(exc)
        sys.exit(f"No DB password. Set $USAGE_DB_PASSWORD or authenticate gcloud.\n{detail}")


def main() -> None:
    ap = argparse.ArgumentParser(description="FinDatEx Validator usage overview (read-only).")
    ap.add_argument("--days", type=int, default=None, help="restrict to the last N days")
    args = ap.parse_args()

    where = f"WHERE received_at > now() - interval '{int(args.days)} days'" if args.days else ""
    and_ = where.replace("WHERE", "AND", 1) if where else ""
    # Since 2026-09 usage_event holds every event kind; the run-centric blocks
    # below look at validation runs only, the Traffic blocks at page views.
    runs = f"{where} AND event_type = 'validate'" if where else "WHERE event_type = 'validate'"
    runs_and = runs.replace("WHERE", "AND", 1)
    views = f"{where} AND event_type = 'page_view'" if where else "WHERE event_type = 'page_view'"

    dsn = (
        f"host={os.environ.get('USAGE_DB_HOST', DEFAULT_HOST)} "
        f"dbname={os.environ.get('USAGE_DB_NAME', DEFAULT_NAME)} "
        f"user={os.environ.get('USAGE_DB_USER', DEFAULT_USER)} "
        f"password={resolve_password()} sslmode=require"
    )
    scope = f"last {args.days} days" if args.days else "all time"
    print(f"FinDatEx Validator — usage overview ({scope})")

    queries = [
        ("Totals", f"""
            SELECT count(*) runs,
                   count(*) FILTER (WHERE source='desktop') desktop_runs,
                   count(*) FILTER (WHERE source='web') web_runs,
                   count(*) FILTER (WHERE mode='batch') batch_runs,
                   count(DISTINCT install_id) FILTER (WHERE source='desktop') installs,
                   coalesce(sum(file_count),0) files, coalesce(sum(row_count),0) rows,
                   min(received_at)::date first_event, max(received_at)::date last_event
            FROM usage_event {runs};"""),
        ("Per day (max. 14)", f"""
            SELECT received_at::date d, count(*) runs,
                   count(*) FILTER (WHERE source='desktop') desktop,
                   count(*) FILTER (WHERE source='web') web,
                   count(DISTINCT install_id) FILTER (WHERE source='desktop') installs,
                   round(avg(overall_score),1) avg_score
            FROM usage_event {runs} GROUP BY 1 ORDER BY 1 DESC LIMIT 14;"""),
        ("Active desktop installs (rolling windows)", """
            SELECT count(DISTINCT install_id) FILTER (WHERE received_at > now() - interval '7 days') d7,
                   count(DISTINCT install_id) FILTER (WHERE received_at > now() - interval '28 days') d28,
                   count(DISTINCT install_id) FILTER (WHERE received_at > now() - interval '90 days') d90,
                   count(DISTINCT install_id) total
            FROM usage_event WHERE source='desktop' AND event_type = 'validate';"""),
        ("Source × mode", f"""
            SELECT source, mode, count(*) runs, round(avg(file_count),1) avg_files,
                   round(avg(row_count)) avg_rows, round(avg(duration_ms)) avg_ms
            FROM usage_event {runs} GROUP BY 1,2 ORDER BY 1,2;"""),
        ("Templates & versions", f"""
            SELECT template_id, template_version, count(*) runs,
                   count(*) FILTER (WHERE source='desktop') desktop,
                   count(*) FILTER (WHERE source='web') web,
                   round(avg(overall_score),1) avg_score,
                   round(avg(error_count),1) avg_errors,
                   round(count(*) FILTER (WHERE error_count=0)*100.0/count(*),1) pct_clean
            FROM usage_event {runs} GROUP BY 1,2 ORDER BY 3 DESC;"""),
        ("Profiles", f"""
            SELECT template_id, p AS profile, count(*) runs
            FROM usage_event, unnest(profiles) p {runs} GROUP BY 1,2 ORDER BY 1, 3 DESC;"""),
        ("Score distribution", f"""
            SELECT CASE WHEN overall_score >= 90 THEN '90-100'
                        WHEN overall_score >= 75 THEN '75-89'
                        WHEN overall_score >= 50 THEN '50-74'
                        WHEN overall_score IS NULL THEN '(none)'
                        ELSE '<50' END bucket, count(*) runs
            FROM usage_event {runs} GROUP BY 1 ORDER BY 1 DESC;"""),
        ("Duration percentiles (ms)", f"""
            SELECT source,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY duration_ms) p50,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) p95,
                   max(duration_ms) max_ms, max(row_count) max_rows
            FROM usage_event WHERE duration_ms IS NOT NULL {runs_and} GROUP BY 1 ORDER BY 1;"""),
        ("Most-triggered rules", f"""
            SELECT r AS rule_id, count(*) runs,
                   round(count(*)*100.0/(SELECT count(*) FROM usage_event {runs}),1) pct_of_runs
            FROM usage_event, unnest(rule_ids) r {runs} GROUP BY 1 ORDER BY 2 DESC LIMIT 25;"""),
        ("External validation (GLEIF/OpenFIGI)", f"""
            SELECT source, external_enabled, count(*) runs
            FROM usage_event {runs} GROUP BY 1,2 ORDER BY 1,2;"""),
        ("Countries", f"""
            SELECT coalesce(country_code,'??') cc, count(*) runs,
                   count(DISTINCT install_id) FILTER (WHERE source='desktop') installs
            FROM usage_event {runs} GROUP BY 1 ORDER BY 2 DESC LIMIT 15;"""),
        ("App versions × OS", f"""
            SELECT source, coalesce(app_version,'?') app_version, coalesce(os_name,'?') os,
                   count(*) runs, count(DISTINCT install_id) installs, max(received_at)::date last_seen
            FROM usage_event {runs} GROUP BY 1,2,3 ORDER BY 1, 6 DESC, 4 DESC LIMIT 20;"""),
        # --- Events & traffic ---------------------------------------------
        # Runs alone cannot say whether a quiet week means nobody came or
        # everybody left without uploading; page_view rows (same table since
        # 2026-09) supply the other half of that ratio.
        ("Events by type & status", f"""
            SELECT event_type, status, source, count(*) n,
                   count(DISTINCT visitor_hash) FILTER (WHERE device IS DISTINCT FROM 'bot') web_visitors
            FROM usage_event {where} GROUP BY 1,2,3 ORDER BY 1,2,3;"""),
        ("Web visitors (daily hash, no bots) — last 14 days", f"""
            SELECT received_at::date d, count(DISTINCT visitor_hash) visitors,
                   count(*) FILTER (WHERE event_type='page_view') views,
                   count(*) FILTER (WHERE event_type='validate') runs,
                   count(*) FILTER (WHERE event_type='report_download') downloads
            FROM usage_event WHERE source='web' AND device IS DISTINCT FROM 'bot' {and_}
            GROUP BY 1 ORDER BY 1 DESC LIMIT 14;"""),
        ("Devices & OS (web)", f"""
            SELECT coalesce(device,'?') device, coalesce(os_name,'?') os, count(*) events,
                   count(DISTINCT visitor_hash) visitors
            FROM usage_event WHERE source='web' {and_} GROUP BY 1,2 ORDER BY 3 DESC;"""),
        ("Input files (format / naming pattern / size)", f"""
            SELECT source, coalesce(input_format,'?') format, coalesce(name_pattern,'?') pattern,
                   count(*) runs, round(percentile_cont(0.5) WITHIN GROUP (ORDER BY input_bytes)/1024) p50_kb,
                   round(max(input_bytes)/1024) max_kb,
                   count(*) FILTER (WHERE is_sample) sample_runs
            FROM usage_event {runs} GROUP BY 1,2,3 ORDER BY 1, 4 DESC;"""),
        ("Failed runs by class", f"""
            SELECT status, source, count(*) n, max(received_at)::date last_seen
            FROM usage_event WHERE status <> 'ok' {runs_and} GROUP BY 1,2 ORDER BY 3 DESC;"""),
        ("External lookups (GLEIF/OpenFIGI)", f"""
            SELECT source, count(*) runs, sum(ext_lookups) lookups, sum(ext_cache_hits) cache_hits,
                   round(percentile_cont(0.5) WITHIN GROUP (ORDER BY ext_duration_ms)) p50_ms,
                   sum(ext_errors) errors
            FROM usage_event WHERE external_enabled {runs_and} GROUP BY 1 ORDER BY 1;"""),
        ("Traffic — totals", f"""
            SELECT (SELECT count(*) FROM usage_event {views}) views,
                   (SELECT count(*) FROM usage_event WHERE source='web' {runs_and}) web_runs,
                   (SELECT CASE WHEN count(*) > 0 THEN round(
                        (SELECT count(*) FROM usage_event WHERE source='web' {runs_and})
                        * 100.0 / count(*), 1) END FROM usage_event {views}) pct_validated,
                   (SELECT min(received_at)::date FROM usage_event {views}) first_view,
                   (SELECT max(received_at)::date FROM usage_event {views}) last_view;"""),
        ("Traffic — per day (max. 14)", f"""
            WITH v AS (SELECT received_at::date d, count(*) views
                       FROM usage_event {views} GROUP BY 1),
                 r AS (SELECT received_at::date d, count(*) runs
                       FROM usage_event WHERE source='web' {runs_and} GROUP BY 1)
            SELECT coalesce(v.d, r.d) d, coalesce(v.views,0) views,
                   coalesce(r.runs,0) web_runs,
                   CASE WHEN coalesce(v.views,0) > 0
                        THEN round(coalesce(r.runs,0)*100.0/v.views,1) END pct_validated
            FROM v FULL OUTER JOIN r ON v.d = r.d
            ORDER BY 1 DESC LIMIT 14;"""),
        ("Traffic — sources (referrer host)", f"""
            SELECT coalesce(referrer_host,'(direct / none)') referrer, count(*) views,
                   max(received_at)::date last_seen
            FROM usage_event {views} GROUP BY 1 ORDER BY 2 DESC LIMIT 20;"""),
        ("Traffic — campaigns (?utm_source= / ?ref=)", f"""
            SELECT coalesce(campaign,'(organic / none)') campaign, count(*) views,
                   min(received_at)::date first_seen, max(received_at)::date last_seen
            FROM usage_event {views} GROUP BY 1 ORDER BY 2 DESC LIMIT 20;"""),
        ("Traffic — pages", f"""
            SELECT path, count(*) views FROM usage_event {views}
            GROUP BY 1 ORDER BY 2 DESC LIMIT 20;"""),
        ("Traffic — countries", f"""
            SELECT coalesce(country_code,'?') cc, count(*) views
            FROM usage_event {views} GROUP BY 1 ORDER BY 2 DESC LIMIT 20;"""),
        ("Quick feedback — ratings", f"""
            SELECT source, count(*) n, round(avg(rating),2) avg_rating,
                   count(*) FILTER (WHERE rating=5) five, count(*) FILTER (WHERE rating<=2) low,
                   count(*) FILTER (WHERE comment IS NOT NULL AND comment <> '') with_comment
            FROM quick_feedback {where} GROUP BY 1 ORDER BY 1;"""),
        ("Quick feedback — latest 20", f"""
            SELECT received_at::timestamp(0) at, source, rating, left(comment, 100) comment,
                   app_version, template_id, country_code cc
            FROM quick_feedback {where} ORDER BY received_at DESC LIMIT 20;"""),
        ("DB size", """
            SELECT pg_size_pretty(pg_total_relation_size('usage_event')) usage_event_size,
                   pg_size_pretty(pg_total_relation_size('quick_feedback')) feedback_size,
                   pg_size_pretty(pg_database_size(current_database())) db_size,
                   (SELECT count(*) FROM usage_event) usage_rows,
                   (SELECT count(*) FROM usage_event WHERE event_type = 'page_view') page_view_rows,
                   (SELECT count(*) FROM quick_feedback) feedback_rows;"""),
    ]

    with psycopg.connect(dsn, connect_timeout=20) as conn:
        for title, sql in queries:
            print(f"\n### {title}")
            with conn.cursor() as cur:
                # One missing table (an older DB without `page_view`, say) must
                # not abort the whole report — and a failed statement poisons
                # the transaction, so roll back before continuing.
                try:
                    cur.execute(sql)
                except psycopg.Error as exc:
                    conn.rollback()
                    print(f"(unavailable: {str(exc).splitlines()[0]})")
                    continue
                cols = [d.name for d in cur.description]
                rows = cur.fetchall()
                widths = [len(c) for c in cols]
                for r in rows:
                    for i, v in enumerate(r):
                        widths[i] = max(widths[i], len("" if v is None else str(v)))
                print("  ".join(c.ljust(widths[i]) for i, c in enumerate(cols)))
                for r in rows:
                    print("  ".join(("" if v is None else str(v)).ljust(widths[i]) for i, v in enumerate(r)))
                if not rows:
                    print("(no rows)")


if __name__ == "__main__":
    main()
