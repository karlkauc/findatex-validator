# Contributing to FinDatEx Validator

Thanks for your interest in contributing! This project is a multi-module
Maven build that validates FinDatEx data-template files (TPT, EET, EMT,
EPT) and ships as both a JavaFX desktop app and a Quarkus + React web
app. Before you open a PR, please skim this short guide.

By participating in this project you agree to abide by the
[Code of Conduct](CODE_OF_CONDUCT.md). Security issues should **not** be
filed as public issues — see [`SECURITY.md`](SECURITY.md) instead.

---

## Ways to contribute

- **Report bugs** via [GitHub Issues](https://github.com/karlkauc/tpt-validator/issues/new/choose)
  using the "Bug report" template. Include the template + version, the
  affected UI (Desktop / Web / Docker), and a minimal reproducer.
  Never attach real fund data.
- **Request features** with the "Feature request" template. Tell us
  *what* problem you're trying to solve, not just the proposed
  implementation.
- **Improve the docs** — typo fixes, clarifications, and missing
  examples are very welcome.
- **Add a new template version** — usually a config-only change. See
  [Adding a new template version](#adding-a-new-template-version) below.
- **Fix bugs / add rules** — open an issue first if it's a non-trivial
  change so we can agree on scope before you write code.

---

## Development setup

Requirements:

- **Java 21** (Temurin recommended). `mvn -v` should report
  `Java version: 21`.
- **Maven 3.9+**.
- **Git**.
- Linux / macOS / Windows. Node 20 is bundled by `frontend-maven-plugin`
  during the web-app build — no host install required.
- Optional: **Docker** if you want to test the container image.

Clone and bootstrap:

```bash
git clone https://github.com/karlkauc/tpt-validator.git
cd tpt-validator
mvn -B -DskipTests package      # one-off build to populate Maven cache
mvn test                        # ~520 tests, must be green before any PR
```

Useful per-module commands:

```bash
# Desktop UI (JavaFX)
mvn -pl javafx-app javafx:run                     # dev run
xvfb-run mvn -pl javafx-app javafx:run            # headless smoke test (CI does this)

# Web layer
mvn -pl web-app -am quarkus:dev                   # backend dev mode (live reload)
(cd web-app/src/main/frontend && npm run dev)     # vite on :5173 (proxies /api → :8080)

# Targeted tests
mvn -Dtest=ClassName test                         # one test class
mvn -Dtest='*ExampleSamplesTest' test             # all per-template sample regressions
mvn clean verify                                  # full regression + JaCoCo
```

`docs/` and [`CLAUDE.md`](CLAUDE.md) contain the architectural map.
[`CLAUDE.md`](CLAUDE.md) is the single source of truth for module
boundaries, naming conventions, and where each kind of code lives —
read it before your first PR.

---

## Pull request workflow

1. **Open an issue first** for anything non-trivial (new rule, new
   template, public API change). Drive-by typo fixes can go straight to
   a PR.
2. **Fork** and create a branch from `main`. Use a descriptive name:
   `fix/eet-isin-format`, `feat/add-tpt-v8`, `docs/contributing`.
3. **Write or update tests** for any behaviour change. Coverage is
   enforced by `mvn verify` (JaCoCo). Bug fixes should come with a
   regression test that fails before the fix and passes after.
4. **Run `mvn verify` locally** before pushing. CI runs the same command
   plus a Docker smoke build — anything green locally on JDK 21 should
   stay green in CI.
5. **Open the PR** against `main`. Fill in the PR template (it appears
   automatically). Reference the issue with `Fixes #123` if applicable.
6. **Address review feedback** with new commits — don't force-push the
   branch during review unless asked. The maintainer will squash-merge
   on landing if appropriate.

A maintainer will review within a few days. Small, focused PRs land
faster than large ones — split mechanical refactors from semantic
changes.

---

## Coding conventions

The project is internally consistent; mimic the surrounding code rather
than introducing new patterns. The non-obvious rules:

### Module boundaries (hard rule)

- `core/` is **UI-agnostic**. No JavaFX (`javafx.*`), no Jakarta EE
  (`jakarta.*`), no Quarkus (`io.quarkus.*`) imports — ever. The whole
  point of the multi-module split is so the web container doesn't drag
  in 50 MB of JavaFX runtime, and the desktop user gets no Quarkus heap
  overhead.
- `javafx-app/` and `web-app/` both depend on `core/`, never on each
  other.
- Package root is `com.findatex.validator` (the directory name
  `tpt_test` is historical). Don't rename it back to `com.tpt.*`.

### Adding a new template version

This is a **config-only** change in 99 % of cases:

1. Drop the spec XLSX into `core/src/main/resources/spec/<template>/`.
   Use a normalised filename (e.g. `TPT_V8_20260601.xlsx`).
2. Author a sibling `*-info.json` manifest. The schema is the
   `SpecManifest` Jackson record — `tpt-v7-info.json` is a worked
   example. Key fields: `sheetName`, `firstDataRow`, 1-based column
   indices, `applicabilityColumns`, `profileColumns`.
3. Add a `TemplateVersion` constant in the per-template `*Template.java`
   and include it in `versions()`.
4. `mvn test` — `TemplateRegistryTest` and the per-template
   `*SpecLoaderTest` / `*RuleSetTest` pick up the new version
   automatically.

Do **not** extend the legacy `SpecLoader` (the hand-written TPT V7
loader). Always go through `ManifestDrivenSpecLoader`.

### Validation rules

- Cross-field rules live in `validation/rules/crossfield/`.
- Prefer `ConditionalRequirement` + `ConditionalFieldPresenceRule` for
  any "if field X = Y then Z is mandatory" rule, before writing a new
  cross-field class.
- For non-TPT templates, **never invent regulatory logic**. EET / EMT /
  EPT rule sets are intentionally mechanical only (presence + format +
  codification + spec-explicit conditional presence). Anything
  regulatory (SFDR, MiFID II, PRIIPs RTS) goes behind a
  `// DEFERRED: requires regulatory SME — <which regulation>` comment.

### Tests

- JUnit 5 + AssertJ. Mirror the package layout (`core/.../validation/`
  → `core/src/test/java/.../validation/`).
- Sample-driven tests live under `core/src/test/resources/sample/` and
  `samples/<template>/`. If your change requires a new fixture,
  regenerate via the matching `tools/build_*_samples.py` rather than
  editing XLSX by hand.

### UI strings

Display strings shown to users come from `ProfileKey.displayName()` and
are byte-identical to the historical TPT enum labels. **Preserve them**
unless you're explicitly changing user-facing copy (and then update the
Excel report's Findings sheet too).

### Commit messages

We use **Conventional Commits**:

```
feat: add EET v1.1.4 manifest and rule set
fix(core): tolerate trailing whitespace in CIC codes
docs: clarify external-validation env vars
chore(deps): bump quarkus to 3.18.0
```

Common types: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`,
`build`, `ci`. Optional scope is the module or area
(`core`, `web`, `javafx`, `deps`, `tests`).

---

## What we won't accept

- Hardcoded field codes added back into `ExternalValidationService`
  (each `TemplateDefinition` declares its ISIN/LEI columns —
  template-agnostic by design).
- Edits to files under `specs/` (operator drop zone, not source).
- Real fund data files (`20260331_TPTV7_*.xlsx`, anything under
  `.DAV/`) — these are gitignored for good reason.
- New abstractions without a concrete second use case. Three similar
  lines beats a premature framework.
- Test changes that delete failing tests instead of fixing the
  underlying bug.

---

## Questions?

Open a [Discussion](https://github.com/karlkauc/tpt-validator/discussions)
or a draft PR and tag it with `[help wanted]`.
