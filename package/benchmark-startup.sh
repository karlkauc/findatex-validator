#!/usr/bin/env bash
# Measure jpackage launcher cold-start time across archive variants:
#
#   none — no CDS / AOT (vanilla launcher, splash + tiered JIT + low Xms)
#   cds  — dynamic CDS archive baked into the image
#   aot  — JEP 483/514 AOT cache (JDK 24+ only)
#
# Each variant is built into its own portable app-image under
# javafx-app/target/bench-<variant>/. We then launch the binary RUNS times
# in training mode (App.java auto-exits after Stage.show() — the moment the
# user would see the UI), timing wall-clock from process start to exit.
#
# Output: per-variant mean / median / stddev / min / max / p95, plus
# pairwise speedups against the "none" baseline.
#
# Env overrides:
#   RUNS         Runs per variant (default 5).
#   VARIANTS     Space-separated subset: "none cds aot" (default all 3).
#   TRAINING_MS  Stage.show()-to-exit delay in the timed runs (default 200).
#                Lower = closer to "time to first paint" but higher noise.
#   FORCE        1 → always rebuild every variant. Default 0 (skip if the
#                bundle already exists from a prior run).
#
# Pre-req: `mvn -DskipTests package` to produce the shaded jar.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_DIR="$PROJECT_DIR/javafx-app/target"

RUNS="${RUNS:-5}"
TRAINING_MS="${TRAINING_MS:-200}"
VARIANTS="${VARIANTS:-none cds aot}"
FORCE="${FORCE:-0}"
APP_NAME="${APP_NAME:-FinDatEx Validator}"

UNAME_S="$(uname -s)"
case "$UNAME_S" in
  Darwin) LAUNCHER_REL="$APP_NAME.app/Contents/MacOS/$APP_NAME" ;;
  *)      LAUNCHER_REL="$APP_NAME/bin/$APP_NAME" ;;
esac

ls "$TARGET_DIR"/findatex-validator-javafx-*-shaded.jar >/dev/null 2>&1 || {
  echo "No shaded jar in $TARGET_DIR — run 'mvn -pl javafx-app -am -DskipTests package' first." >&2
  exit 1
}

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 required for stats — install or symlink python3 onto PATH." >&2
  exit 2
fi

# Variant flag matrix: what to pass to jpackage.sh for each label.
flags_for_variant() {
  case "$1" in
    none) echo "ENABLE_CDS=0 ENABLE_AOT=0" ;;
    cds)  echo "ENABLE_CDS=1 ENABLE_AOT=0" ;;
    aot)  echo "ENABLE_CDS=0 ENABLE_AOT=1" ;;
    *) echo "Unknown variant: $1" >&2; return 1 ;;
  esac
}

build_variant() {
  local v="$1"
  local out="$TARGET_DIR/bench-$v"

  if [[ "$FORCE" != "1" && -x "$out/$LAUNCHER_REL" ]]; then
    echo "  → reusing existing $out"
    return 0
  fi

  rm -rf "$out"
  mkdir -p "$out"

  local flags
  flags="$(flags_for_variant "$v")"
  echo "  → building variant '$v' ($flags) into $out"
  # ENABLE_SPLASH stays 1 across all variants — splash is "free" perceived
  # speedup we want to keep on all configurations being compared. The thing
  # we're isolating is the archive, not the splash.
  env $flags ENABLE_SPLASH=1 PACKAGE_TYPE=app-image OUT_DIR="$out" \
    bash "$SCRIPT_DIR/jpackage.sh" >"$out/build.log" 2>&1 || {
      echo "  ✗ build failed — see $out/build.log"
      tail -20 "$out/build.log" >&2
      return 1
    }
}

# Wall-clock timer using python3 (cross-platform; macOS BSD date lacks %N).
time_run() {
  local launcher="$1"
  python3 -c '
import os, sys, subprocess, time
launcher = sys.argv[1]
training_ms = sys.argv[2]
env = os.environ.copy()
# The launcher reads its .cfg (which already has the right -Dfindatex.training
# from training-mode patching? No — we strip it post-training, so we need to
# re-inject for the benchmark. _JAVA_OPTIONS works as long as the value has
# no spaces; -Dfindatex.training=<ms> is space-free.)
env["_JAVA_OPTIONS"] = f"-Dfindatex.training={training_ms}"
# xvfb-run if no DISPLAY (CI / headless server). On a real desktop this just
# runs against the actual X server.
needs_xvfb = (sys.platform.startswith("linux")
              and not env.get("DISPLAY"))
cmd = [launcher]
if needs_xvfb:
    if subprocess.run(["which", "xvfb-run"], capture_output=True).returncode != 0:
        print("xvfb-run missing", file=sys.stderr); sys.exit(2)
    cmd = ["xvfb-run", "--auto-servernum"] + cmd
t0 = time.perf_counter()
rc = subprocess.run(cmd, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode
t1 = time.perf_counter()
if rc != 0:
    print(f"launcher exit={rc}", file=sys.stderr); sys.exit(rc)
print(f"{(t1-t0):.3f}")
' "$launcher" "$TRAINING_MS"
}

bench_variant() {
  local v="$1"
  local out="$TARGET_DIR/bench-$v"
  local launcher="$out/$LAUNCHER_REL"
  if [[ ! -x "$launcher" ]]; then
    echo "  ✗ launcher not found at $launcher — skipping"
    return 1
  fi
  echo
  echo "Benchmarking '$v' ($RUNS runs)…"
  local times=()
  # Warm-up run (page-cache priming) — discarded.
  time_run "$launcher" >/dev/null 2>&1 || true
  for ((i=1; i<=RUNS; i++)); do
    local t
    t="$(time_run "$launcher")"
    times+=("$t")
    printf "  run %2d: %ss\n" "$i" "$t"
  done
  printf '%s\n' "${times[@]}" > "$TARGET_DIR/bench-$v.times"
}

# Build all variants requested (skipping those already built unless FORCE=1).
for v in $VARIANTS; do
  build_variant "$v" || exit $?
done

# Run all benchmarks.
for v in $VARIANTS; do
  bench_variant "$v" || true
done

# Aggregate + print table.
python3 - <<'PY' "$TARGET_DIR" $VARIANTS
import sys, os, statistics

target_dir = sys.argv[1]
variants = sys.argv[2:]

rows = []
for v in variants:
    p = os.path.join(target_dir, f"bench-{v}.times")
    if not os.path.exists(p):
        continue
    with open(p) as f:
        ts = [float(x) for x in f.read().split() if x.strip()]
    if not ts:
        continue
    ts_sorted = sorted(ts)
    n = len(ts)
    p95_idx = max(0, min(n-1, int(round(0.95 * (n-1)))))
    rows.append({
        "variant": v,
        "n":       n,
        "mean":    statistics.fmean(ts),
        "median":  statistics.median(ts),
        "stdev":   statistics.stdev(ts) if n > 1 else 0.0,
        "min":     min(ts),
        "max":     max(ts),
        "p95":     ts_sorted[p95_idx],
    })

if not rows:
    print("No benchmark data — did all builds succeed?", file=sys.stderr)
    sys.exit(1)

print()
print("=" * 72)
hdr = f"{'variant':<8}{'n':>4}  {'mean':>8}  {'median':>8}  {'stdev':>7}  {'min':>7}  {'p95':>7}"
print(hdr)
print("-" * 72)
for r in rows:
    print(f"{r['variant']:<8}{r['n']:>4}  {r['mean']:>7.3f}s  {r['median']:>7.3f}s  "
          f"{r['stdev']:>6.3f}s  {r['min']:>6.3f}s  {r['p95']:>6.3f}s")
print("=" * 72)

# Pairwise speedup table — relative to "none" baseline if present, else
# relative to the slowest variant.
baseline = next((r for r in rows if r["variant"] == "none"), None) or max(rows, key=lambda r: r["median"])
print()
print(f"Speedup vs. '{baseline['variant']}' (median):")
for r in rows:
    if r is baseline:
        continue
    delta = baseline["median"] - r["median"]
    pct   = (delta / baseline["median"]) * 100 if baseline["median"] else 0.0
    print(f"  {r['variant']:<6}  {delta:+.3f}s  ({pct:+.1f}%)")
PY
