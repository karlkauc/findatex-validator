#!/usr/bin/env bash
# Records the desktop-app walkthrough GIFs for the README.
#
#   tools/demo/record_desktop_demo.sh            # → docs/screenshots/desktop-*.gif
#   KEEP_FRAMES=1 tools/demo/record_desktop_demo.sh   # keep the raw frames for inspection
#
# Needs: the shaded desktop jar (mvn -pl javafx-app -am -DskipTests package),
# xvfb-run, ffmpeg, python3 + Pillow. Runs the app in an isolated HOME so the
# developer's settings are untouched and no usage event is posted.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAR="$(ls -t "$ROOT"/javafx-app/target/findatex-validator-javafx-*-shaded.jar | head -1)"
WORK="${WORK:-$(mktemp -d)}"
FRAMES="$WORK/frames"
HOME_DIR="$WORK/home"
OUT="${OUT:-$ROOT/docs/screenshots}"

echo "[demo] jar:    $JAR"
echo "[demo] work:   $WORK"

# --- demo data: a copy of the showcase file under a realistic name, plus a
#     folder of deliveries for the batch scene. The recorder is started with
#     cwd=$HOME_DIR so the path shown in the UI is the short relative one.
mkdir -p "$HOME_DIR/.config/findatex-validator" "$HOME_DIR/Downloads/tpt-deliveries"
cp "$ROOT/samples/tpt/00_showcase.xlsx" "$HOME_DIR/Downloads/2026-06-30_TPT_V7_Showcase.xlsx"
#     The fixture names ("01_clean", "07_weights_dont_sum") would read as a
#     verdict in the Files table, so they get delivery-style names.
cp "$ROOT/samples/tpt/00_showcase.xlsx"               "$HOME_DIR/Downloads/tpt-deliveries/Global_Equity_2026-06-30.xlsx"
cp "$ROOT/samples/tpt/01_clean.xlsx"                  "$HOME_DIR/Downloads/tpt-deliveries/Money_Market_2026-06-30.xlsx"
cp "$ROOT/samples/tpt/03_bad_formats.xlsx"            "$HOME_DIR/Downloads/tpt-deliveries/Corporate_Bonds_2026-06-30.xlsx"
cp "$ROOT/samples/tpt/07_weights_dont_sum.xlsx"       "$HOME_DIR/Downloads/tpt-deliveries/Multi_Asset_2026-06-30.xlsx"
cp "$ROOT/samples/tpt/13_multi_fund_with_errors.xlsx" "$HOME_DIR/Downloads/tpt-deliveries/Umbrella_Fund_2026-06-30.xlsx"
cat > "$HOME_DIR/.config/findatex-validator/settings.json" <<'EOF'
{
 "external": {"enabled": false,
              "lei": {"enabled": true, "checkLapsedStatus": true, "checkIssuerName": false, "checkIssuerCountry": false},
              "isin": {"enabled": true, "openFigiApiKey": "", "checkCurrency": false, "checkCicConsistency": false},
              "cache": {"ttlDays": 7, "directory": ""}},
 "proxy": {"mode": "NONE", "manual": {"host": "", "port": 0, "user": "", "passwordEncrypted": "", "nonProxyHosts": ""}},
 "feedback": {"githubRepo": ""},
 "usageStats": {"enabled": false, "installId": "", "endpointUrl": ""},
 "newsletter": {"endpointUrl": ""},
 "quickFeedback": {"endpointUrl": ""}
}
EOF

# --- record
rm -rf "$FRAMES"
(
    cd "$HOME_DIR"
    xvfb-run -a -s "-screen 0 1280x800x24" \
        java -Duser.home="$HOME_DIR" -Dprism.order=sw -Dglass.gtk.uiScale=1 \
             --add-opens javafx.fxml/javafx.fxml=ALL-UNNAMED \
             -cp "$JAR" "$ROOT/tools/demo/DesktopDemoRecorder.java" \
             "$FRAMES" "Downloads/2026-06-30_TPT_V7_Showcase.xlsx" "Downloads/tpt-deliveries" \
        2>&1 | grep -v "^[0-9:.]* DEBUG" || true
)
if [ -f "$FRAMES/FAILED" ]; then
    echo "[demo] recorder failed: $(cat "$FRAMES/FAILED")" >&2
    exit 1
fi

# --- compose
python3 "$ROOT/tools/demo/build_demo_gif.py" "$FRAMES" "$OUT"

if [ -z "${KEEP_FRAMES:-}" ]; then
    rm -rf "$WORK"
else
    echo "[demo] frames kept in $FRAMES"
fi
