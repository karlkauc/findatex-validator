# FinDatEx Validator

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![CI](https://github.com/karlkauc/findatex-validator/actions/workflows/ci.yml/badge.svg)](https://github.com/karlkauc/findatex-validator/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/temurin/releases/?version=21)
[![Container](https://img.shields.io/badge/container-ghcr.io-blue?logo=docker)](https://github.com/karlkauc/findatex-validator/pkgs/container/findatex-validator)

Validate FinDatEx data-template files (TPT, EET, EMT, EPT) against the
official spec. Two delivery modes share the same validation core:

- **Desktop (JavaFX)** — files never leave your machine. Best for daily
  validation of confidential fund data.
- **Web (Quarkus + React, Docker)** — same engine behind an upload form.
  Useful when desktop install is blocked, or for self-service in-house
  deployments.

Bundled templates: **TPT V7.0 + V6.0**, **EET V1.1.3 + V1.1.2**,
**EMT V4.3 + V4.2**, **EPT V2.1 + V2.0**.

End-user help (what gets validated, profiles, GLEIF/OpenFIGI lookup):
[`HELP.md`](core/src/main/resources/help/HELP.md) — also reachable from
the Help button in either UI.

---

## Quick start

### 1. Desktop app (recommended for daily use)

You have two options.

**Native installer** *(no Java required on the target machine)*:

```bash
./package/jpackage.sh        # Linux .deb / .rpm or macOS .dmg
package\jpackage.bat         # Windows .msi
```

The resulting installer puts a `FinDatEx Validator` entry in your start
menu / applications list. Files are read locally, the report is written
locally, and there is no network traffic except the optional GLEIF /
OpenFIGI lookup.

**Run from source** *(Java 21 required)*:

```bash
mvn -pl javafx-app javafx:run                                   # dev mode
mvn -pl javafx-app -am -DskipTests package                      # build fat JAR
java -jar javafx-app/target/findatex-validator-javafx-1.0.0-shaded.jar
```

### 2. Hosted web instance

> **Public URL:** `<YOUR_HOSTED_INSTANCE_URL>` *(to be filled in once a
> public instance is available)*.

The hosted web instance accepts an `.xlsx` / `.xlsm` / `.csv` upload
(max 25 MB), runs the same validation engine as the desktop app, and
returns a quality report plus a single-use Excel download URL valid for
5 minutes. No login. Per-IP rate limiting and a body-size cap protect
the instance against abuse.

Privacy posture of the hosted instance:

- Uploads are processed in memory and discarded the moment the response
  is sent.
- Reports are kept in memory until first download or 5-minute TTL,
  whichever comes first, then deleted.
- No file content is logged or persisted.
- External validation (GLEIF/OpenFIGI) is **off** unless the operator
  has enabled it server-side.

### 3. Self-hosted Docker (in-house deployment)

A multi-stage `Dockerfile` plus `docker-compose.yml` are provided. The
default container binds to `127.0.0.1:18082` and is intended to sit
behind a reverse proxy (nginx / Traefik / caddy) that handles TLS and
auth if needed.

```bash
git clone https://github.com/karlkauc/findatex-validator.git
cd findatex-validator
docker compose up -d
# → http://127.0.0.1:18082
```

A pre-built image is also published on each release tag:

```bash
docker pull ghcr.io/karlkauc/findatex-validator:latest
```

To customise behaviour, set the env vars in `docker-compose.yml` (or
your orchestrator). Defaults below; override only what you need.

| Env var | Default | What it does |
|---------|---------|--------------|
| `FINDATEX_WEB_RATE_LIMIT_PER_IP_PER_HOUR` | `10` | Per-IP token-bucket on `POST /api/validate`. Returns HTTP 429 on overflow. |
| `FINDATEX_WEB_MAX_CONCURRENCY`            | `4`  | Cap on simultaneous validations across the instance. Returns HTTP 429 on overflow. |
| `FINDATEX_WEB_ACQUIRE_TIMEOUT_MILLIS`     | `2000` | How long a request waits for a concurrency permit before returning 429. |
| `FINDATEX_WEB_REPORT_TTL_MINUTES`         | `5`  | Excel report download window. |
| `QUARKUS_HTTP_LIMITS_MAX_BODY_SIZE`       | `25M`| Upload size cap. Larger uploads return HTTP 413. |
| `FINDATEX_WEB_EXTERNAL_ENABLED`           | `false` | Operator master switch for GLEIF/OpenFIGI lookup. UI per-request toggle is ignored unless this is `true`. |
| `FINDATEX_WEB_EXTERNAL_OPENFIGI_KEY`      | *(empty)* | OpenFIGI API key. Optional; raises rate limit from 4 to 100 req/s. Users may also provide a per-request key (never stored). |
| `FINDATEX_WEB_EXTERNAL_PROXY_MODE`        | `NONE` | One of `NONE`, `SYSTEM`, `MANUAL`. Container outbound network policy. |
| `FINDATEX_WEB_EXTERNAL_PROXY_HOST`        | *(empty)* | Manual proxy host. |
| `FINDATEX_WEB_EXTERNAL_PROXY_PORT`        | `0`  | Manual proxy port. |
| `FINDATEX_WEB_EXTERNAL_PROXY_USERNAME`    | *(empty)* | Manual proxy username (NTLM-aware). |
| `FINDATEX_WEB_EXTERNAL_PROXY_PASSWORD`    | *(empty)* | Manual proxy password. Pass via secret manager, not as a literal in compose. |
| `FINDATEX_WEB_EXTERNAL_PROXY_NON_PROXY_HOSTS` | *(empty)* | Comma-separated bypass list (e.g. `localhost,*.internal`). |
| `FINDATEX_WEB_EXTERNAL_CACHE_DIR`         | `/tmp/findatex-cache` | Persistent identifier-result cache. Mount a volume to keep it across restarts. |
| `FINDATEX_WEB_EXTERNAL_CACHE_TTL_DAYS`    | `7`  | Cache entry lifetime. |

Health check: `GET /_internal/health/ready` (returns `200 OK` once
templates have been loaded). The default compose file already wires
this into Docker's healthcheck.

Minimum host: any Linux with Docker 20.10+, 512 MB free RAM, ~1 GB disk
for the image. The image bundles a custom 21-module jlink JRE — no
host Java install needed.

---

## Architecture

```
findatex-validator/
├── core/         UI-agnostic validation, scoring, ingest, reports (~520 tests)
├── javafx-app/   Desktop UI (depends on core only)
└── web-app/      Quarkus REST + React SPA (depends on core only)
```

The validation flow is identical across UIs:

```
TemplateRegistry.of(id)
   → specLoaderFor(version).load()        // SpecCatalog
   → TptFileLoader(catalog).load(...)     // TptFile
   → ValidationEngine(catalog, ruleSet)
        .validate(file, profiles)         // List<Finding>
   → FindingEnricher.enrich
   → QualityScorer / XlsxReportWriter     // QualityReport + .xlsx
```

The only difference is the loader entry point: JavaFX uses `load(Path)`
(file picker), the web layer uses `load(InputStream, filename)` (no
tempfile written through).

Each template lives in its own package under `core/.../template/<t>/`
with a `*Template`, `*Profiles`, and `*RuleSet` class. New template
versions are config-only: drop the spec XLSX, author a sibling
`*-info.json` manifest, register one constant in the per-template
`*Template.java` (see "Adding a new template version" below).

---

## Configuration

### Desktop app

Settings live in `~/.config/findatex-validator/settings.json` (Linux),
`~/Library/Application Support/findatex-validator/settings.json` (macOS),
or `%APPDATA%\findatex-validator\settings.json` (Windows). The proxy
password is encrypted with a machine-bound AES key — copying the file
to another machine will silently invalidate the password but not the
rest of the settings. All settings are reachable via Settings… in the
toolbar.

### Web app

Configuration is via env vars (table above) or
`web-app/src/main/resources/application.properties`. The Quarkus
`@ConfigProperty` names follow `findatex.web.*`; all are overridable
via `FINDATEX_WEB_*` environment variables.

---

## Adding a new template version

Adding a new version of an existing template requires no Java changes.

1. Drop the spec XLSX into `core/src/main/resources/spec/<template>/`.
   Use a normalised name (e.g. `TPT_V8_20260601.xlsx`).
2. Author a sibling `*-info.json` manifest. The schema is the
   `SpecManifest` record — `tpt-v7-info.json` is a worked example. Key
   fields: sheet name, `firstDataRow`, 1-based column indices,
   `applicabilityColumns` (`kind: "CIC"` for TPT or `"none"` otherwise),
   `profileColumns` (`kind: "flag"` or `"presenceMerge"`).
3. Add a `TemplateVersion` constant in the per-template
   `*Template.java` and include it in the `versions()` list.
4. `mvn test` — `TemplateRegistryTest` and the per-template
   `*SpecLoaderTest` / `*RuleSetTest` will pick up the new version
   automatically.

The UI's `MainController` probes `specLoaderFor(latest()).load()` per
template at startup and silently downgrades a template to a "Spec not
installed" placeholder tab if loading throws — so a manifest typo
won't break the rest of the UI.

Adding a brand-new template (rather than a version) requires writing
the per-template `*Template`, `*Profiles`, and `*RuleSet` classes, plus
registering the new `TemplateId` enum value. See the existing TPT
implementation for the worked example.

---

## Building and testing

Java 21 + Maven 3.9+. Node and npm are bundled by `frontend-maven-plugin`
during the web-app build — no host install required.

```bash
mvn test                                       # ~520 tests (all modules)
mvn -DskipTests package                        # build everything
mvn -pl core -am test                          # core only
mvn -Dtest=ClassName test                      # one test class
mvn -Dtest='*ExampleSamplesTest' test          # all per-template sample regressions
mvn clean verify                               # full regression + JaCoCo report

# Desktop
mvn -pl javafx-app javafx:run                  # dev run
xvfb-run mvn -pl javafx-app javafx:run         # headless smoke test

# Web
mvn -pl web-app -am quarkus:dev                # backend dev mode
(cd web-app/src/main/frontend && npm run dev)  # vite on :5173
mvn -pl web-app -am -P backend-only -DskipTests package   # backend without npm rebuild

# Container
docker build -t findatex-validator-web:1.0.0 .
docker compose up -d

# Generators
python3 tools/build_samples.py                 # core test samples
python3 tools/build_examples.py                # samples/tpt/*
python3 tools/build_eet_samples.py             # samples/eet/*  (also _emt_, _ept_)
python3 tools/generate_requirements.py         # rebuild requirements.md
./package/jpackage.sh                          # native desktop installer
```

---

## Privacy and data handling

| Mode | What happens to your data |
|------|---------------------------|
| **Desktop**         | Files are read from disk, validated locally, and the report is written locally. The only outbound network call is the optional GLEIF / OpenFIGI lookup — and only the identifiers in your file (LEIs, ISINs) are sent, not the full file. |
| **Hosted web**      | You upload to the operator's server. Files are processed in memory and discarded immediately after the response. Reports live for 5 minutes via a single-use URL, then are deleted. No login, no per-file logging. External validation off by default. |
| **Self-hosted Docker** | Same defaults as the hosted web mode, but on infrastructure you control. Operator decides whether to enable external validation, what proxy mode to use, and how long reports are retained. |

---

## Security

Found a vulnerability? Please **do not open a public issue**. See
[SECURITY.md](SECURITY.md) and use GitHub's private vulnerability
reporting (Repository → *Security* → *Advisories* → *Report a
vulnerability*). The maintainer will acknowledge within 72 hours.

## Author

Created and maintained by **Karl Kauc**
([karl.kauc@gmail.com](mailto:karl.kauc@gmail.com),
[github.com/karlkauc](https://github.com/karlkauc)).

## License

Released under the [Apache License 2.0](LICENSE). You may use, modify,
and distribute the source under the terms of that license; the patent
grant in §3 applies to all contributions.

## Contributing

Bug reports, feature requests, and pull requests are welcome. See
[`CONTRIBUTING.md`](CONTRIBUTING.md) for the development setup, coding
conventions, and PR checklist, and [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md)
for the community standards. Security issues should be reported privately
via the process described in [`SECURITY.md`](SECURITY.md).

---

## Links

- [`HELP.md`](core/src/main/resources/help/HELP.md) — end-user help
  (rendered in the apps via the Help button).
- [`CLAUDE.md`](CLAUDE.md) — developer guide (architecture, conventions,
  test structure).
- [`docs/SPEC_DOWNLOADS.md`](docs/SPEC_DOWNLOADS.md) — operator
  checklist for acquiring spec XLSX files from FinDatEx.
- [`docs/SPEC_INVENTORY.md`](docs/SPEC_INVENTORY.md) — auto-maintained
  list of bundled spec files.
- [`samples/`](samples/) — per-template scenario fixtures (clean +
  broken variants).
