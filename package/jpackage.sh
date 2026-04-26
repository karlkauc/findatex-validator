#!/usr/bin/env bash
# Build a Linux native installer (.deb on Debian/Ubuntu, .rpm on Red Hat) using jpackage.
# Run after `mvn -DskipTests package`.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_DIR="$PROJECT_DIR/target"
APP_NAME="TPT Validator"
APP_VERSION="1.0.0"
SHADED_JAR="$TARGET_DIR/tpt-validator-${APP_VERSION}-shaded.jar"

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

# Auto-detect type based on package manager.
if command -v dpkg >/dev/null 2>&1; then
  PKG_TYPE="deb"
elif command -v rpm >/dev/null 2>&1; then
  PKG_TYPE="rpm"
else
  PKG_TYPE="app-image"
fi

INPUT_DIR="$TARGET_DIR/jpackage-input"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"
cp "$SHADED_JAR" "$INPUT_DIR/"

ICON_ARG=()
if [[ -f "$SCRIPT_DIR/icon.png" ]]; then
  ICON_ARG=(--icon "$SCRIPT_DIR/icon.png")
fi

jpackage \
  --type "$PKG_TYPE" \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "TPT Validator" \
  --description "Quality and conformance validator for TPT V7 files" \
  --input "$INPUT_DIR" \
  --main-jar "$(basename "$SHADED_JAR")" \
  --main-class com.tpt.validator.AppLauncher \
  "${ICON_ARG[@]}" \
  --dest "$OUT_DIR"

echo "Installer artefacts at $OUT_DIR:"
ls -la "$OUT_DIR"
