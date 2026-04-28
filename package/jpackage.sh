#!/usr/bin/env bash
# Build a native installer using jpackage:
#   - Linux:  .deb (Debian/Ubuntu) or .rpm (Red Hat)  [icon: package/icon.png]
#   - macOS:  .dmg / .pkg                              [icon: package/icon.icns]
#   - other:  app-image fallback
#
# Run after `mvn -DskipTests package`.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_DIR="$PROJECT_DIR/javafx-app/target"
APP_NAME="FinDatEx Validator"
APP_VERSION="1.0.0"
SHADED_JAR="$TARGET_DIR/findatex-validator-javafx-${APP_VERSION}-shaded.jar"

if [[ ! -f "$SHADED_JAR" ]]; then
  echo "Shaded JAR not found at $SHADED_JAR — run 'mvn -DskipTests package' first." >&2
  exit 1
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage not found on PATH (needs JDK 17+)." >&2
  exit 2
fi

OUT_DIR="$TARGET_DIR/installer"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

INPUT_DIR="$TARGET_DIR/jpackage-input"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"
cp "$SHADED_JAR" "$INPUT_DIR/"

# Detect platform.
UNAME_S="$(uname -s)"
EXTRA_ARGS=()
ICON_PATH=""

case "$UNAME_S" in
  Darwin)
    PKG_TYPE="dmg"
    ICON_PATH="$SCRIPT_DIR/icon.icns"
    EXTRA_ARGS+=(
      --mac-package-name "TPT Validator"
      --mac-package-identifier "com.findatex.validator"
    )
    ;;
  Linux)
    if command -v dpkg >/dev/null 2>&1; then
      PKG_TYPE="deb"
    elif command -v rpm >/dev/null 2>&1; then
      PKG_TYPE="rpm"
    else
      PKG_TYPE="app-image"
    fi
    ICON_PATH="$SCRIPT_DIR/icon.png"
    EXTRA_ARGS+=(
      --linux-shortcut
      --linux-app-category "Office"
    )
    ;;
  *)
    PKG_TYPE="app-image"
    ICON_PATH="$SCRIPT_DIR/icon.png"
    ;;
esac

ICON_ARG=()
if [[ -n "$ICON_PATH" && -f "$ICON_PATH" ]]; then
  ICON_ARG=(--icon "$ICON_PATH")
fi

echo "Building $PKG_TYPE installer for $UNAME_S using ${ICON_ARG[*]:-no icon}..."

jpackage \
  --type "$PKG_TYPE" \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "TPT Validator" \
  --description "Quality and conformance validator for TPT V7 files" \
  --input "$INPUT_DIR" \
  --main-jar "$(basename "$SHADED_JAR")" \
  --main-class com.findatex.validator.AppLauncher \
  "${ICON_ARG[@]}" \
  "${EXTRA_ARGS[@]}" \
  --dest "$OUT_DIR"

echo "Installer artefacts at $OUT_DIR:"
ls -la "$OUT_DIR"
