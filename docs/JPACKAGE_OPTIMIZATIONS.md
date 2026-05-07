# jpackage Startup Optimizations

This document explains the cold-start optimizations baked into
`package/jpackage.sh` / `package/jpackage.bat` and how to measure their
impact with `package/benchmark-startup.sh`.

The optimizations are stacked: each one is independently switchable via env
var so you can A/B-compare. On this app, the headline result is **~13 %
faster cold start with AOT cache** (≈ 1.3 s on a 10 s baseline) — the
remaining time is dominated by POI XLSX-spec deserialisation, which no JVM
flag can shorten.

## What the script bakes in

| Switch         | Default            | Effect                                                    |
| -------------- | ------------------ | --------------------------------------------------------- |
| `ENABLE_AOT`   | `1` on JDK ≥ 24    | JEP 483/514 AOT cache (`app.aot`)                         |
| `ENABLE_CDS`   | `1` on JDK < 24    | Dynamic CDS archive (`app.jsa`); off when AOT is on       |
| `ENABLE_SPLASH`| `1`                | `-splash:$APPDIR/splash.png` baked into `.cfg`            |
| (always)       |                    | `-Xms128m` (was 512m) — smaller initial heap commit       |
| (always)       |                    | `-XX:TieredStopAtLevel=1` — C1-only JIT, faster warmup    |
| `TRAINING_MS`  | `2500`             | Stage.show()-to-exit delay during the training run        |

All other jpackage settings (icon, vendor, package type, modules) are
unchanged from the previous build.

## The three-stage flow

The script can no longer be a single jpackage call because CDS/AOT archives
must be **trained against the bundled JVM** before being baked into the
final installer.

```
┌───────────────────────────────────────────────────────────────┐
│ Stage 1 — intermediate app-image                              │
│   jpackage --type app-image …                                 │
│   (no archive flag yet — archive doesn't exist to reference)  │
└─────────────────────────────┬─────────────────────────────────┘
                              ▼
┌───────────────────────────────────────────────────────────────┐
│ Stage 2 — training run                                        │
│   1. copy $JAVA_HOME/bin/java into the bundle's runtime/bin   │
│      (jpackage strips it; AOT assembly needs a child java)    │
│   2. patch launcher .cfg with                                 │
│        -Dfindatex.training=$TRAINING_MS                       │
│        -XX:AOTCacheOutput=$APPDIR/app.aot     (or)            │
│        -XX:ArchiveClassesAtExit=$APPDIR/app.jsa               │
│   3. run launcher under xvfb-run if no DISPLAY                │
│   4. App.java's hook auto-exits after Stage.show() + delay    │
│   5. revert .cfg, delete bundled java.exe + .config artefact  │
└─────────────────────────────┬─────────────────────────────────┘
                              ▼
┌───────────────────────────────────────────────────────────────┐
│ Stage 3 — final wrap                                          │
│   - PACKAGE_TYPE=app-image: `cp -a` (mtimes preserved!)       │
│   - any installer type: jpackage --app-image <stage1>         │
│   .cfg now has -XX:AOTCache / -XX:SharedArchiveFile baked in  │
└───────────────────────────────────────────────────────────────┘
```

## The training mode in App.java

Setting `-Dfindatex.training=<ms>` activates a one-shot exit hook
(`App.maybeScheduleTrainingExit`) that fires after `Stage.show()`:

```text
1. wait <ms> ms (default 2000) — lets TemplateRegistry probes finish so the
   archive captures realistically warmed classes
2. Platform.runLater(Platform::exit)
3. wait 1500 ms grace period
4. System.exit(0) hard fallback
```

The hard exit is needed under xvfb / headless because the GTK runloop
sometimes doesn't process `Platform.exit()`. JVM shutdown hooks still run,
so dynamic-CDS / AOT cache dumps complete.

## Each optimization in isolation

### 1. Low `-Xms`

`-Xms128m` instead of the previous `-Xms512m`. Eliminates ~384 MiB of
upfront heap commit at process start. Heap still grows to `-Xmx8g` on
demand for batch runs (no max heap change). Saves ≈ 100–200 ms.

### 2. `-XX:TieredStopAtLevel=1`

Limits the JIT to C1 (client compiler) only, skipping C2 (server compiler).
For a GUI app that isn't numerics-bound, this is a clear win: faster JIT
warmup, no C2 compile storm during startup. POI XLSX parsing is I/O-bound,
so the lower C1 peak throughput doesn't matter here.

### 3. Splash screen

`-splash:$APPDIR/splash.png` is a JVM flag that paints `splash.png` via the
JRE's native splash code **before any class loading**. The splash window
disappears automatically when the first AWT/Swing/JavaFX window shows.
Doesn't make startup faster — makes it *feel* faster (perceived ≈ 1 s gain).

### 4. CDS dynamic archive (`app.jsa`)

JDK 13+ feature. Writes a "top-up" archive of all classes loaded during
training run, layered on top of the JDK's built-in CDS archive. Skips
class file parsing + bytecode verification on subsequent launches.

Activated by patching `.cfg` with:
```
java-options=-XX:SharedArchiveFile=$APPDIR/app.jsa
```

Used as the default on JDK < 24. On this app: marginal (~2 %) because
spec-loading dominates startup time.

### 5. AOT cache (`app.aot`) — JDK 24+

JEP 483 ("Ahead-of-Time Class Loading & Linking") + JEP 514 (single-step
`AOTCacheOutput`). Subsumes CDS: stores fully *linked* classes plus method
profiles. Bigger startup win than CDS because it skips both parsing AND
linking.

Activated by patching `.cfg` with:
```
java-options=-XX:AOTCache=$APPDIR/app.aot
```

Used as the default on JDK ≥ 24. **The 13 % win in the benchmark is from
this single switch.**

## Gotchas (the painful ones)

These all bit during implementation. Future-proofing notes:

### `-J<flag>` is jpackage build-time only

The launcher binary **silently ignores** `-J<flag>` arguments at runtime.
This is a one-way ticket to debugging: `time launcher -J-Xlog:aot` looks
right, but the JVM never sees the flag. Two correct mechanisms:

- **Bake into `.cfg`**: `java-options=...` lines. Composes with
  `--java-options` from the original `jpackage` invocation. Recommended
  for any flag that contains a path.
- **`_JAVA_OPTIONS` env var**: parsed by the JVM regardless of how it was
  launched. **But** the JVM splits this on whitespace, so any value
  containing a path with spaces (e.g. `"FinDatEx Validator"`) is mangled
  beyond recognition. OK for path-free flags like `-Dfindatex.training=200`.

### AOT/CDS path-sensitivity

The archive encodes the classpath JAR's **absolute path** captured at
training time. At runtime, the JVM compares against the actual classpath:

| Build target                | Match?                                              |
| --------------------------- | --------------------------------------------------- |
| `app-image` (portable)      | ✓ build-path == install-path (same `$APPDIR`)       |
| `.deb` / `.rpm` / `.msi`    | ✗ install moves bundle to `/opt/...` or `Program Files` |

For installer types the JVM warns once and falls back to vanilla CDS.
Workaround would be a post-install training hook (out of scope; the
remaining tax is small because vanilla CDS still helps).

### AOT must train against the bundled JVM

The AOT cache encodes a hash of the runtime's `lib/modules` image. A cache
built against the system `java` binary references the system's full
`lib/modules`; the slim jlink-built bundle has a smaller `lib/modules`,
and the JVM rejects the cache at load time:

```
[warning][aot] This file is not the one used while building the shared
archive file: '…/lib/runtime/lib/modules', size has changed
```

But jpackage **strips `bin/java` from the bundled runtime by default** —
the launcher binary replaces it. AOT cache assembly spawns a child `java`
process for the create step (JEP 483 record/create flow). Solution: copy
`$JAVA_HOME/bin/java` into the bundle's `lib/runtime/bin/` for the
duration of training, then delete it (the script does this automatically).

### Preserve mtimes when relocating the bundle

The AOT cache verifies the classpath JAR's **mtime** in addition to the
path. `cp -R src dst` doesn't preserve mtimes by default on Linux/macOS —
the next launch will reject the cache with:

```
[warning][aot] This file is not the one used while building the shared
archive file: '…/findatex-validator-javafx-X.Y.Z-shaded.jar',
timestamp has changed
```

Use:
- Linux/macOS: `cp -a` (archive mode)
- Windows: `robocopy /COPYALL` (the script uses this)

### `Platform.exit()` can hang under xvfb

The JavaFX GTK runloop sometimes doesn't process the exit event when
running headless. The training hook in `App.java` schedules a hard
`System.exit(0)` 1.5 s after `Platform.exit()` as a fallback. JVM
shutdown hooks (which is what writes the AOT cache / dynamic CDS
archive) still run on `System.exit(0)`, so the artefact is produced.

## Benchmarking

`package/benchmark-startup.sh` builds three portable variants in parallel
target dirs and times cold-starts:

```bash
RUNS=5 ./package/benchmark-startup.sh                    # all three variants
VARIANTS="none aot" ./package/benchmark-startup.sh       # subset
RUNS=10 TRAINING_MS=200 ./package/benchmark-startup.sh   # tighter exit
FORCE=1 ./package/benchmark-startup.sh                   # rebuild from scratch
```

Each variant goes into `javafx-app/target/bench-<variant>/`. Build is
skipped on subsequent runs unless `FORCE=1`.

The script:
1. invokes `jpackage.sh` with the matching `ENABLE_CDS` / `ENABLE_AOT` env
   so each variant only differs in the archive flag (splash + tiered + low
   `-Xms` are held constant — the comparison isolates the archive choice)
2. runs each launcher under xvfb-run (if no DISPLAY) with
   `_JAVA_OPTIONS=-Dfindatex.training=$TRAINING_MS` so it auto-exits
3. discards a warm-up run, then times `RUNS` runs via `python3
   time.perf_counter()`
4. reports mean / median / stdev / min / p95 + median speedup vs the `none`
   baseline

Sample output (5 runs, JDK 25.0.2 LTS, Linux):

```
variant    n      mean    median    stdev      min      p95
none       5    9.987s   10.027s   0.165s   9.712s  10.140s
cds        5    9.823s    9.793s   0.080s   9.732s   9.931s
aot        5    8.701s    8.727s   0.135s   8.543s   8.888s

Speedup vs. 'none' (median):
  cds     +0.234s  (+2.3%)
  aot     +1.300s  (+13.0%)
```

### Reading the numbers

The 10 s baseline isn't all JVM startup — about 5 s of it is `POI`
deserialising the EMT V4.3 spec XLSX (which has lots of formulas). CDS and
AOT only speed up the *JVM-side* portion (class loading, linking,
JIT warmup), so the headline percentages understate the relative
improvement on JVM startup itself. To see the pure JVM win, you'd need to
defer the spec probing — see "What's left on the table" below.

## What's left on the table

The biggest remaining cold-start cost on this app is **not JVM startup**
but the eager probe of all 8 spec XLSXs (4 templates × 2 versions) in
`MainController` before the first tab is shown. Lazy per-tab loading would
roughly halve the perceived startup time — much bigger than the AOT win.
That's a code change rather than a build-time tweak and was out of scope
for this round.

GraalVM `native-image` would eliminate JVM startup entirely (~50 ms
launch) but POI's reflection load makes Substrate configuration a
multi-week effort. Not pursued.

## File reference

| Path                              | Purpose                                                  |
| --------------------------------- | -------------------------------------------------------- |
| `package/jpackage.sh`             | Linux/macOS build; orchestrates the 3-stage flow         |
| `package/jpackage.bat`            | Windows pendant                                          |
| `package/benchmark-startup.sh`    | A/B benchmark across none / CDS / AOT variants           |
| `package/splash.png`              | Splash image bundled into the launcher                   |
| `javafx-app/.../App.java`         | `maybeScheduleTrainingExit()` hook used by the training run |
