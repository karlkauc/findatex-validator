#!/usr/bin/env bash
# Build a native installer or portable app-image for the JavaFX desktop UI.
#
# Defaults:
#   - Linux:  .deb (Debian/Ubuntu) or .rpm (RHEL)   icon: package/icon.png
#   - macOS:  .dmg                                  icon: package/icon.icns
#   - other:  app-image
#
# Pipeline (always two-stage so we can warm a CDS / AOT archive):
#   1. Build an intermediate app-image (full jlink runtime).
#   2. Run the bundled launcher in training mode (App.java exits after Stage.show()).
#      This dumps either a dynamic-CDS archive or an AOT cache into the app/ dir.
#   3. Bake -XX:SharedArchiveFile / -XX:AOTCache into the launcher .cfg by re-running
#      jpackage with --app-image, then producing the final installer (or portable).
#
# Run after `mvn -DskipTests package` so the shaded jar exists.
#
# Env overrides:
#   APP_VERSION    Installer version (default: derived from the shaded jar
#                  filename so it tracks pom.xml automatically).
#   APP_VENDOR     Vendor / publisher (default "Karl Kauc").
#   APP_NAME       Display name (default "FinDatEx Validator").
#   PACKAGE_TYPE   Override jpackage --type. Set to "app-image" to skip the
#                  OS installer and produce a portable directory in
#                  $TARGET_DIR/portable instead of $TARGET_DIR/installer.
#   JAVA_OPTIONS   JVM flags baked into the launcher (default:
#                  "-Xms128m -Xmx8g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m
#                   -XX:TieredStopAtLevel=1").
#                  -Xms is intentionally low so cold-start doesn't commit a
#                  big heap; -Xmx still caters to the 800-file batch case.
#                  TieredStopAtLevel=1 keeps the JIT in C1 — JavaFX is not
#                  numerics-bound and we want fast warmup over peak throughput.
#   ENABLE_SPLASH  1 (default) → -splash:$APPDIR/splash.png baked in.
#   ENABLE_CDS     1 (default) → dynamic-CDS archive (app.jsa) generated and used.
#                  Mutually exclusive with ENABLE_AOT (AOT subsumes CDS).
#   ENABLE_AOT     1 (default if JDK >= 24) → JEP 483/514 AOT cache (app.aot).
#                  Falls back to 0 on older JDKs.
#   TRAINING_MS    How long the training run stays alive after Stage.show()
#                  before exiting (default 2500). Longer = more classes
#                  recorded = bigger but more useful archive.
#
# The shaded jar already contains JavaFX classes + native libraries, so we
# do NOT pass --module-path / --add-modules javafx.* — that would split-package
# against the classpath copy. We pass a curated list of JDK modules so jlink
# can build a slim runtime that still works on vanilla Temurin.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_DIR="$PROJECT_DIR/javafx-app/target"

APP_NAME="${APP_NAME:-FinDatEx Validator}"
APP_VENDOR="${APP_VENDOR:-Karl Kauc}"
TRAINING_MS="${TRAINING_MS:-2500}"

# Discover the shaded jar by glob — pom.xml's <version> is in the filename, so
# this tracks bumps automatically without re-reading the pom.
SHADED_JAR="$(ls -1 "$TARGET_DIR"/findatex-validator-javafx-*-shaded.jar 2>/dev/null | head -n 1 || true)"
if [[ -z "$SHADED_JAR" || ! -f "$SHADED_JAR" ]]; then
  echo "No findatex-validator-javafx-*-shaded.jar in $TARGET_DIR — run 'mvn -DskipTests package' first." >&2
  exit 1
fi

# Default APP_VERSION from the jar name so the installer label matches the
# build by default; CI still overrides via env to pin to the git tag.
if [[ -z "${APP_VERSION:-}" ]]; then
  APP_VERSION="$(basename "$SHADED_JAR" | sed -E 's/^findatex-validator-javafx-(.+)-shaded\.jar$/\1/')"
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage not found on PATH (needs JDK 17+)." >&2
  exit 2
fi

# Detect the local JDK feature version once — drives ENABLE_AOT default.
JDK_FEATURE="$(java -version 2>&1 | awk -F'"' '/version/ {split($2, a, "."); print a[1]; exit}')"
JDK_FEATURE="${JDK_FEATURE:-0}"

ENABLE_SPLASH="${ENABLE_SPLASH:-1}"
ENABLE_CDS="${ENABLE_CDS:-1}"
if [[ -z "${ENABLE_AOT:-}" ]]; then
  if (( JDK_FEATURE >= 24 )); then ENABLE_AOT=1; else ENABLE_AOT=0; fi
fi

# AOT subsumes CDS — never run both.
if [[ "$ENABLE_AOT" == "1" && "$ENABLE_CDS" == "1" ]]; then
  ENABLE_CDS=0
fi
if [[ "$ENABLE_AOT" == "1" && "$JDK_FEATURE" -lt 24 ]]; then
  echo "ENABLE_AOT=1 requires JDK 24+, but local JDK is $JDK_FEATURE — disabling AOT." >&2
  ENABLE_AOT=0
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
case "$UNAME_S" in
  Darwin) ICON_PATH="$SCRIPT_DIR/icon.icns" ;;
  *)      ICON_PATH="$SCRIPT_DIR/icon.png"  ;;
esac
if [[ "$PACKAGE_TYPE" != "app-image" ]]; then
  case "$UNAME_S" in
    Darwin) EXTRA_ARGS+=(--mac-package-identifier "com.findatex.validator") ;;
    Linux)  EXTRA_ARGS+=(--linux-shortcut --linux-app-category "Office") ;;
  esac
fi

if [[ -z "${OUT_DIR:-}" ]]; then
  if [[ "$PACKAGE_TYPE" == "app-image" ]]; then
    OUT_DIR="$TARGET_DIR/portable"
  else
    OUT_DIR="$TARGET_DIR/installer"
  fi
fi
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

# Stage 1 inputs: shaded jar plus optional splash. Both end up next to the
# launcher in the produced image's app/ directory and are addressed at runtime
# via $APPDIR (substituted by jpackage in --java-options).
INPUT_DIR="$TARGET_DIR/jpackage-input"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"
cp "$SHADED_JAR" "$INPUT_DIR/"
if [[ "$ENABLE_SPLASH" == "1" && -f "$SCRIPT_DIR/splash.png" ]]; then
  cp "$SCRIPT_DIR/splash.png" "$INPUT_DIR/splash.png"
fi

# JDK 25's refactored BuildEnvBuilder NPEs when --temp is omitted (auto-temp
# path is broken — Objects.requireNonNull on Path root). Harmless on JDK 21.
TEMP_DIR_STAGE1="$TARGET_DIR/jpackage-temp-stage1"
TEMP_DIR_STAGE2="$TARGET_DIR/jpackage-temp-stage2"
rm -rf "$TEMP_DIR_STAGE1" "$TEMP_DIR_STAGE2"
mkdir -p "$TEMP_DIR_STAGE1" "$TEMP_DIR_STAGE2"

ICON_ARG=()
if [[ -n "$ICON_PATH" && -f "$ICON_PATH" ]]; then
  ICON_ARG=(--icon "$ICON_PATH")
fi

# JDK modules baked into jlink's slim runtime. We previously ran jdeps
# --print-module-deps, but on JDKs with bundled JavaFX (Liberica Full,
# Azul FX) split-package detection returns a truncated list missing
# java.logging / java.xml / java.management. The launcher then dies
# silently on first JavaFX logger call. Using a curated list adds ~10 MB
# to the runtime but guarantees the app starts.
ADD_MODULES="java.base,java.desktop,java.naming,java.net.http,java.scripting,java.security.jgss,java.sql,java.xml,java.xml.crypto,java.logging,java.management,jdk.crypto.ec,jdk.jfr,jdk.unsupported"

# Heap settings + JIT tuning baked into the launcher binary. Cold-start with
# -Xms128m commits ~128 MiB rather than the previous 512 MiB; the heap still
# grows to 8 GiB on demand for batch runs.
JAVA_OPTIONS="${JAVA_OPTIONS:--Xms128m -Xmx8g -XX:+UseG1GC -XX:MaxMetaspaceSize=512m -XX:TieredStopAtLevel=1}"

# Splash & archive options use $APPDIR which jpackage substitutes at runtime
# to the app/ subdir. Backslash-escaped here so the shell doesn't expand it.
ARCHIVE_RUNTIME_OPTS=""
if [[ "$ENABLE_AOT" == "1" ]]; then
  ARCHIVE_RUNTIME_OPTS='-XX:AOTCache=$APPDIR/app.aot'
elif [[ "$ENABLE_CDS" == "1" ]]; then
  ARCHIVE_RUNTIME_OPTS='-XX:SharedArchiveFile=$APPDIR/app.jsa'
fi

SPLASH_OPT=""
if [[ "$ENABLE_SPLASH" == "1" && -f "$INPUT_DIR/splash.png" ]]; then
  SPLASH_OPT='-splash:$APPDIR/splash.png'
fi

# Assemble java-options for stage 1. No archive flag here — the archive
# doesn't exist yet, and baking a missing-file reference into the .cfg would
# print a JVM warning on every launch. After the training run we append the
# archive flag to the .cfg directly. Inlined (no function) to stay
# bash-3.2-clean on macOS.
STAGE1_OPTS=()
for opt in $JAVA_OPTIONS; do
  STAGE1_OPTS+=(--java-options "$opt")
done
if [[ -n "$SPLASH_OPT" ]]; then
  STAGE1_OPTS+=(--java-options "$SPLASH_OPT")
fi

echo "Building $PACKAGE_TYPE for $UNAME_S — version $APP_VERSION, vendor '$APP_VENDOR'"
echo "  add-modules:    $ADD_MODULES"
echo "  java-options:   $JAVA_OPTIONS"
echo "  splash:         $([[ -n "$SPLASH_OPT" ]] && echo "$SPLASH_OPT" || echo "(off)")"
echo "  archive:        $([[ -n "$ARCHIVE_RUNTIME_OPTS" ]] && echo "$ARCHIVE_RUNTIME_OPTS" || echo "(none — vanilla CDS only)")"
echo "  training delay: ${TRAINING_MS} ms"

# ---- Stage 1: intermediate app-image ------------------------------------
INTERMEDIATE_DIR="$TARGET_DIR/jpackage-stage1"
rm -rf "$INTERMEDIATE_DIR"
mkdir -p "$INTERMEDIATE_DIR"

# macOS bash 3.2 is set -u-allergic to "${arr[@]}" on empty arrays — guard with
# the "${arr[@]+...}" idiom which expands to nothing when unset.
jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "$APP_VENDOR" \
  --description "FinDatEx data-template validator (TPT, EET, EMT, EPT)" \
  --input "$INPUT_DIR" \
  --main-jar "$(basename "$SHADED_JAR")" \
  --main-class com.findatex.validator.AppLauncher \
  --add-modules "$ADD_MODULES" \
  ${STAGE1_OPTS[@]+"${STAGE1_OPTS[@]}"} \
  ${ICON_ARG[@]+"${ICON_ARG[@]}"} \
  --temp "$TEMP_DIR_STAGE1" \
  --dest "$INTERMEDIATE_DIR"

# ---- Locate the bundled launcher + app/ dir for the training run --------
case "$UNAME_S" in
  Darwin)
    APP_BUNDLE="$INTERMEDIATE_DIR/$APP_NAME.app"
    LAUNCHER_BIN="$APP_BUNDLE/Contents/MacOS/$APP_NAME"
    APP_DIR_RUNTIME="$APP_BUNDLE/Contents/app"
    ;;
  *)
    APP_BUNDLE="$INTERMEDIATE_DIR/$APP_NAME"
    LAUNCHER_BIN="$APP_BUNDLE/bin/$APP_NAME"
    APP_DIR_RUNTIME="$APP_BUNDLE/lib/app"
    ;;
esac

if [[ ! -x "$LAUNCHER_BIN" ]]; then
  echo "Stage-1 launcher not found at $LAUNCHER_BIN — jpackage layout changed?" >&2
  exit 3
fi

# ---- Stage 2: training run → dump archive --------------------------------
# We must use the BUNDLED JVM (not system java) because the AOT cache /
# dynamic-CDS archive encodes the runtime's `lib/modules` image hash. A cache
# built against the system JVM will be rejected at launcher runtime with
# "size has changed". jpackage doesn't ship lib/runtime/bin/java — we copy the
# system `java` binary in for the duration of the training, then drop it.
ARCHIVE_FILE=""
if [[ "$ENABLE_AOT" == "1" || "$ENABLE_CDS" == "1" ]]; then
  RUNTIME_BIN_DIR="$APP_BUNDLE/lib/runtime/bin"
  RUNTIME_JAVA="$RUNTIME_BIN_DIR/java"
  case "$UNAME_S" in
    Darwin) RUNTIME_BIN_DIR="$APP_BUNDLE/Contents/runtime/Contents/Home/bin"
            RUNTIME_JAVA="$RUNTIME_BIN_DIR/java" ;;
  esac
  mkdir -p "$RUNTIME_BIN_DIR"
  SYS_JAVA="$(command -v java || true)"
  if [[ -z "$SYS_JAVA" ]]; then
    echo "No system 'java' on PATH — cannot run training. Skipping archive." >&2
  else
    cp "$SYS_JAVA" "$RUNTIME_JAVA"
    chmod +x "$RUNTIME_JAVA"
  fi

  if [[ "$ENABLE_AOT" == "1" ]]; then
    ARCHIVE_FILE="$APP_DIR_RUNTIME/app.aot"
    ARCHIVE_TRAIN_FLAG="-XX:AOTCacheOutput=$ARCHIVE_FILE"
  else
    ARCHIVE_FILE="$APP_DIR_RUNTIME/app.jsa"
    ARCHIVE_TRAIN_FLAG="-XX:ArchiveClassesAtExit=$ARCHIVE_FILE"
  fi

  echo
  echo "Training run — generating $(basename "$ARCHIVE_FILE") (delay ${TRAINING_MS} ms)…"

  # The cleanest way to inject training-only options is to append them to the
  # launcher's .cfg, run the launcher, then revert. _JAVA_OPTIONS would seem
  # tempting but the JVM splits its value on whitespace, and "FinDatEx
  # Validator" contains a space — so any path-bearing flag (AOTCacheOutput,
  # ArchiveClassesAtExit) gets mangled. -J<flag> on the launcher command line
  # is jpackage-time-only and is silently swallowed at runtime.
  CFG_FILE="$APP_DIR_RUNTIME/$APP_NAME.cfg"
  CFG_BACKUP="$CFG_FILE.pretrain"
  run_training() {
    if [[ ! -f "$CFG_FILE" ]]; then
      echo "  launcher .cfg not found at $CFG_FILE" >&2
      return 1
    fi
    cp "$CFG_FILE" "$CFG_BACKUP"
    {
      echo "java-options=-Dfindatex.training=$TRAINING_MS"
      echo "java-options=$ARCHIVE_TRAIN_FLAG"
    } >> "$CFG_FILE"

    local rc=0
    if [[ "$UNAME_S" == "Linux" && -z "${DISPLAY:-}" ]]; then
      if command -v xvfb-run >/dev/null 2>&1; then
        echo "  no DISPLAY — using xvfb-run"
        xvfb-run --auto-servernum "$LAUNCHER_BIN" || rc=$?
      else
        echo "  no DISPLAY and xvfb-run not installed — skipping training." >&2
        rc=1
      fi
    else
      "$LAUNCHER_BIN" || rc=$?
    fi

    # Revert .cfg so the runtime archive flag we'll append next is the only
    # archive-related entry — no leftover ArchiveClassesAtExit / AOTCacheOutput.
    mv "$CFG_BACKUP" "$CFG_FILE"
    return $rc
  }

  if [[ -x "$RUNTIME_JAVA" ]]; then
    run_training || {
      echo "Training run failed; archive not generated — falling back to no-archive build." >&2
      ARCHIVE_FILE=""
    }
  else
    ARCHIVE_FILE=""
  fi

  # Always remove the borrowed java binary (and any leftover .config) — keeps
  # the shipped image slim and avoids surprising users with a redundant java
  # binary that doesn't match what the launcher actually invokes.
  rm -f "$RUNTIME_JAVA" "$ARCHIVE_FILE.config" 2>/dev/null || true
  if [[ "$RUNTIME_BIN_DIR" != *"Contents/Home/bin" ]]; then
    rmdir "$RUNTIME_BIN_DIR" 2>/dev/null || true
  fi

  if [[ -n "$ARCHIVE_FILE" && ! -f "$ARCHIVE_FILE" ]]; then
    echo "  expected archive not produced at $ARCHIVE_FILE — falling back to no-archive build." >&2
    ARCHIVE_FILE=""
  elif [[ -n "$ARCHIVE_FILE" ]]; then
    echo "  archive: $(du -h "$ARCHIVE_FILE" | cut -f1)  ($ARCHIVE_FILE)"
    # Patch the launcher .cfg so subsequent runs pick up the archive. The .cfg
    # lives next to the shaded jar inside app/ (or lib/app on Linux; Contents/app
    # on macOS). jpackage's appended java-options=... lines compose with the ones
    # baked in via --java-options.
    CFG_FILE="$APP_DIR_RUNTIME/$APP_NAME.cfg"
    if [[ -f "$CFG_FILE" ]]; then
      echo "java-options=$ARCHIVE_RUNTIME_OPTS" >> "$CFG_FILE"
      echo "  patched $(basename "$CFG_FILE") with $ARCHIVE_RUNTIME_OPTS"
    else
      echo "  WARNING: launcher .cfg not found at $CFG_FILE — archive will not be used at runtime." >&2
    fi
  fi
fi

# ---- Stage 3: package the augmented app-image ---------------------------
echo
if [[ "$PACKAGE_TYPE" == "app-image" ]]; then
  # No second jpackage run — the stage-1 bundle (with archive baked in) IS the
  # final portable image. Move it to OUT_DIR so the path matches the docs.
  echo "Final output is portable app-image (no installer wrap)."
  rm -rf "$OUT_DIR"
  mkdir -p "$OUT_DIR"
  cp -a "$APP_BUNDLE" "$OUT_DIR/"
else
  # The stage-1 launcher already has the right --java-options baked into its
  # .cfg (including the archive reference). For installer types we feed that
  # bundle back through jpackage with --app-image; the second pass only adds
  # the installer wrapper, it doesn't rebuild the runtime.
  echo "Wrapping app-image into final $PACKAGE_TYPE…"
  jpackage \
    --type "$PACKAGE_TYPE" \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "$APP_VENDOR" \
    --description "FinDatEx data-template validator (TPT, EET, EMT, EPT)" \
    --app-image "$APP_BUNDLE" \
    ${ICON_ARG[@]+"${ICON_ARG[@]}"} \
    ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"} \
    --temp "$TEMP_DIR_STAGE2" \
    --dest "$OUT_DIR"
fi

echo
echo "Output at $OUT_DIR:"
ls -la "$OUT_DIR"
