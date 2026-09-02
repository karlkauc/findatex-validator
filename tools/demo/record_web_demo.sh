#!/usr/bin/env bash
# Re-records docs/screenshots/web-app-demo.gif against a locally started web app.
#
#   tools/demo/record_web_demo.sh            # builds nothing — needs web-app/target/quarkus-app
#
# Needs: mvn -pl web-app -am -DskipTests package, node + the root package.json's
# Playwright (npm install at the repo root, npx playwright install chromium),
# ffmpeg, python3 + Pillow. Starts the jar on $PORT (default 18090) with the
# optional header actions switched on (desktop download link, GitHub repo for
# "Report", star rating) so the GIF shows what the hosted instance shows. The
# usage DB points at a closed port: the rating widget needs a configured URL to
# appear, and every insert fails silently instead of reaching a real database.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PORT="${PORT:-18090}"
WORK="${WORK:-$(mktemp -d)}"
FRAMES="$WORK/frames"
OUT="${OUT:-$ROOT/docs/screenshots}"
JAR="$ROOT/web-app/target/quarkus-app/quarkus-run.jar"
[ -f "$JAR" ] || { echo "missing $JAR — run: mvn -pl web-app -am -DskipTests package" >&2; exit 1; }

mkdir -p "$WORK"
cp "$ROOT/samples/tpt/00_showcase.xlsx" "$WORK/2026-06-30_TPT_V7_Showcase.xlsx"

echo "[demo] starting web app on :$PORT"
(
    cd "$ROOT/web-app/target/quarkus-app"
    FINDATEX_WEB_DESKTOP_DOWNLOAD_URL="https://github.com/karlkauc/findatex-validator/releases" \
    FINDATEX_WEB_FEEDBACK_GITHUB_REPO="karlkauc/findatex-validator" \
    FINDATEX_WEB_USAGE_DB_URL="jdbc:postgresql://127.0.0.1:1/demo" \
    FINDATEX_WEB_USAGE_DB_USER=demo FINDATEX_WEB_USAGE_DB_PASSWORD=demo \
    FINDATEX_WEB_USAGE_DB_ACQUISITION_TIMEOUT=1s \
    exec java -Dquarkus.http.port="$PORT" -jar quarkus-run.jar > "$WORK/web.log" 2>&1
) &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null || true' EXIT
for _ in $(seq 1 60); do
    curl -fs "http://127.0.0.1:$PORT/api/templates" > /dev/null 2>&1 && break
    sleep 1
done
curl -fs "http://127.0.0.1:$PORT/api/templates" > /dev/null || { echo "web app did not come up, see $WORK/web.log" >&2; exit 1; }

rm -rf "$FRAMES"
(cd "$ROOT" && node tools/demo/record_web_demo.mjs "http://127.0.0.1:$PORT/" "$FRAMES" "$WORK/2026-06-30_TPT_V7_Showcase.xlsx")
python3 "$ROOT/tools/demo/build_demo_gif.py" "$FRAMES" "$OUT" web

if [ -z "${KEEP_FRAMES:-}" ]; then
    rm -rf "$WORK"
else
    echo "[demo] frames kept in $FRAMES"
fi
