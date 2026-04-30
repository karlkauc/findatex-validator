#!/usr/bin/env bash
# Build a native installer or portable app-image for the JavaFX desktop UI.
#
# Defaults:
#   - Linux:  .deb (Debian/Ubuntu) or .rpm (RHEL)   icon: package/icon.png
#   - macOS:  .dmg                                  icon: package/icon.icns
#   - other:  app-image
#
# Run after `mvn -DskipTests package` so the shaded jar exists.
#
# Env overrides:
#   APP_VERSION    Installer version (default 1.0.0).
#   APP_VENDOR     Vendor / publisher (default "Karl Kauc").
#   APP_NAME       Display name (default "FinDatEx Validator").
#   PACKAGE_TYPE   Override jpackage --type. Set to "app-image" to skip the
#                  OS installer and produce a portable directory in
#                  $TARGET_DIR/portable instead of $TARGET_DIR/installer.
#   JAVA_OPTIONS   JVM flags baked into the launcher (default:
#                  "-Xms512m -Xmx8g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m").
#
# The shaded jar already contains JavaFX classes + native libraries, so we
# do NOT pass --module-path / --add-modules javafx.* — that would split-package
# against the classpath copy. We only enumerate the JDK modules we need
# (computed via jdeps with --ignore-missing-deps) so jlink can build a slim
# runtime that still works on vanilla Temurin.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_DIR="$PROJECT_DIR/javafx-app/target"

APP_NAME="${APP_NAME:-FinDatEx Validator}"
APP_VERSION="${APP_VERSION:-1.0.0}"
APP_VENDOR="${APP_VENDOR:-Karl Kauc}"
SHADED_JAR="$TARGET_DIR/findatex-validator-javafx-1.0.0-shaded.jar"

if [[ ! -f "$SHADED_JAR" ]]; then
  echo "Shaded JAR not found at $SHADED_JAR — run 'mvn -DskipTests package' first." >&2
  exit 1
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage not found on PATH (needs JDK 17+)." >&2
  exit 2
fi

UNAME_S="$(uname -s)"
EXTRA_ARGS=()

if [[ -z "${PACKAGE_TYPE:-}" ]]; then
  case "$UNAME_S" in
    Darwin) PACKAGE_TYPE="dmg" ;;
    Linux)
      if   command -v dpkg >/dev/null 2>&1; then PACKAGE_TYPE="deb"
      elif command -v rpm  >/dev/null 2>&1; then PACKAGE_TYPE="rpm"
      else                                      PACKAGE_TYPE="app-image"
      fi
      ;;
    *)      PACKAGE_TYPE="app-image" ;;
  esac
fi

ICON_PATH=""
if [[ "$PACKAGE_TYPE" != "app-image" ]]; then
  case "$UNAME_S" in
    Darwin)
      ICON_PATH="$SCRIPT_DIR/icon.icns"
      EXTRA_ARGS+=(--mac-package-identifier "com.findatex.validator")
      ;;
    Linux)
      ICON_PATH="$SCRIPT_DIR/icon.png"
      EXTRA_ARGS+=(--linux-shortcut --linux-app-category "Office")
      ;;
  esac
else
  case "$UNAME_S" in
    Darwin) ICON_PATH="$SCRIPT_DIR/icon.icns" ;;
    *)      ICON_PATH="$SCRIPT_DIR/icon.png"  ;;
  esac
fi

if [[ "$PACKAGE_TYPE" == "app-image" ]]; then
  OUT_DIR="$TARGET_DIR/portable"
else
  OUT_DIR="$TARGET_DIR/installer"
fi
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

INPUT_DIR="$TARGET_DIR/jpackage-input"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"
cp "$SHADED_JAR" "$INPUT_DIR/"

ICON_ARG=()
if [[ -n "$ICON_PATH" && -f "$ICON_PATH" ]]; then
  ICON_ARG=(--icon "$ICON_PATH")
fi

# Compute JDK modules required by the shaded jar so jlink builds a slim
# runtime. Falls back to a hand-curated list if jdeps is unavailable or
# returns nothing.
ADD_MODULES=""
if command -v jdeps >/dev/null 2>&1; then
  set +e
  # jdeps prints split-package warnings interleaved on stdout; the actual
  # module list is the last non-warning, comma-separated line.
  ADD_MODULES="$(jdeps --multi-release 21 \
                       --ignore-missing-deps \
                       --print-module-deps \
                       "$SHADED_JAR" 2>/dev/null \
                | grep -v '^Warning:' \
                | tail -n 1)"
  set -e
fi
if [[ -z "$ADD_MODULES" ]]; then
  ADD_MODULES="java.base,java.desktop,java.naming,java.net.http,java.scripting,java.security.jgss,java.sql,java.xml,java.xml.crypto,java.logging,java.management,jdk.crypto.ec,jdk.jfr,jdk.unsupported"
fi

# Heap settings baked into the launcher binary. Batch-Mode mit hunderten XLSX-
# Dateien hält pro Datei TptFile + Findings + QualityReport im Speicher; 8 GiB
# sind großzügig für ~800 Dateien dimensioniert. Override via env JAVA_OPTIONS.
JAVA_OPTIONS="${JAVA_OPTIONS:--Xms512m -Xmx8g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m}"
JAVA_OPTION_ARGS=()
for opt in $JAVA_OPTIONS; do
  JAVA_OPTION_ARGS+=(--java-options "$opt")
done

echo "Building $PACKAGE_TYPE for $UNAME_S — version $APP_VERSION, vendor '$APP_VENDOR'"
echo "  add-modules:  $ADD_MODULES"
echo "  java-options: $JAVA_OPTIONS"

jpackage \
  --type "$PACKAGE_TYPE" \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "$APP_VENDOR" \
  --description "FinDatEx data-template validator (TPT, EET, EMT, EPT)" \
  --input "$INPUT_DIR" \
  --main-jar "$(basename "$SHADED_JAR")" \
  --main-class com.findatex.validator.AppLauncher \
  --add-modules "$ADD_MODULES" \
  "${JAVA_OPTION_ARGS[@]}" \
  "${ICON_ARG[@]}" \
  "${EXTRA_ARGS[@]}" \
  --dest "$OUT_DIR"

echo "Output at $OUT_DIR:"
ls -la "$OUT_DIR"
