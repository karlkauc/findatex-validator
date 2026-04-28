# Install & Run — technical guide

This document collects the technical bits that the [README](README.md)
deliberately keeps out of the way: building from source, packaging
native installers locally, and self-hosting the web app with Docker.

If all you want is to *use* the validator, the README has a much
shorter path: download an installer or open the hosted web instance.
Come back here when you want to build, deploy, or contribute.

---

## Contents

1. [Prerequisites](#prerequisites)
2. [Build from source](#build-from-source)
3. [Run the desktop app from source](#run-the-desktop-app-from-source)
4. [Build native installers locally](#build-native-installers-locally)
5. [Run the web app in dev mode](#run-the-web-app-in-dev-mode)
6. [Self-host with Docker](#self-host-with-docker)
7. [Configuration (web app)](#configuration-web-app)
8. [Tests and generators](#tests-and-generators)

---

## Prerequisites

- **Java 21** (Temurin recommended). `mvn -v` should report
  `Java version: 21`.
- **Maven 3.9+**.
- **Git**.
- Linux / macOS / Windows. Node 20 is bundled by `frontend-maven-plugin`
  during the web-app build — no host install required.
- Optional: **Docker 20.10+** if you want to build or run the container
  image.

---

## Build from source

```bash
git clone https://github.com/karlkauc/findatex-validator.git
cd findatex-validator
mvn -B -DskipTests package      # one-off build to populate the Maven cache
mvn test                        # ~520 tests across all modules
```

The reactor builds three modules:

```
findatex-validator/
├── core/         UI-agnostic validation engine
├── javafx-app/   Desktop UI (depends on core only)
└── web-app/      Quarkus REST + React SPA (depends on core only)
```

---

## Run the desktop app from source

```bash
mvn -pl javafx-app javafx:run                                   # dev run
mvn -pl javafx-app -am -DskipTests package                      # build fat JAR
java -jar javafx-app/target/findatex-validator-javafx-1.0.0-shaded.jar
```

Files are read locally, the report is written locally, and there is no
network traffic except the optional GLEIF / OpenFIGI lookup.

---

## Build native installers locally

Any vanilla JDK 21+ works — the shaded jar already contains JavaFX.

```bash
mvn -pl javafx-app -am -DskipTests package        # produces the shaded jar
./package/jpackage.sh                              # Linux .deb / macOS .dmg
PACKAGE_TYPE=app-image ./package/jpackage.sh       # portable directory in javafx-app/target/portable/
package\jpackage.bat                               # Windows .msi
set "PACKAGE_TYPE=app-image" && package\jpackage.bat   # Windows portable
```

CI produces the same artefacts for every tag and attaches them to
[GitHub Releases](https://github.com/karlkauc/findatex-validator/releases).

---

## Run the web app in dev mode

```bash
mvn -pl web-app -am quarkus:dev                # backend dev mode (live reload)
(cd web-app/src/main/frontend && npm run dev)  # vite on :5173 (proxies /api → :8080)
```

Production build:

```bash
mvn -pl web-app -am -DskipTests package                   # → web-app/target/quarkus-app/
mvn -pl web-app -am -P backend-only -DskipTests package   # backend without npm rebuild
```

REST endpoints (all under `/api`):

- `GET  /api/templates` — available templates, versions, profiles
- `POST /api/validate`  — multipart upload → JSON validation response
- `GET  /api/report/{uuid}` — single-use Excel download (5-minute TTL)

---

## Self-host with Docker

A multi-stage `Dockerfile` and a `docker-compose.yml` are provided.
The default container binds to `127.0.0.1:18082` and is intended to
sit behind a reverse proxy (nginx / Traefik / caddy) that handles TLS
and any auth you need.

```bash
git clone https://github.com/karlkauc/findatex-validator.git
cd findatex-validator
docker compose up -d
# → http://127.0.0.1:18082
```

A pre-built image is published on each release tag:

```bash
docker pull ghcr.io/karlkauc/findatex-validator:latest
```

Health check: `GET /_internal/health/ready` returns `200 OK` once
templates have been loaded. The default compose file already wires
this into Docker's healthcheck.

Minimum host: any Linux with Docker 20.10+, 512 MB free RAM, ~1 GB
disk for the image. The image bundles a custom 21-module jlink JRE —
no host Java install needed.

---

## Configuration (web app)

Set env vars in `docker-compose.yml` (or your orchestrator). Defaults
below; override only what you need.

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

The Quarkus `@ConfigProperty` names follow `findatex.web.*`; all are
overridable via the matching `FINDATEX_WEB_*` environment variable.
Defaults are also visible in
`web-app/src/main/resources/application.properties`.

### Desktop-app settings

The desktop app stores its settings (proxy, optional API keys) in:

- Linux: `~/.config/findatex-validator/settings.json`
- macOS: `~/Library/Application Support/findatex-validator/settings.json`
- Windows: `%APPDATA%\findatex-validator\settings.json`

The proxy password is encrypted with a machine-bound AES key — copying
the file to another machine silently invalidates the password but not
the rest of the settings. All settings are reachable via *Settings…*
in the toolbar.

---

## Tests and generators

```bash
mvn test                                       # ~520 tests (all modules)
mvn -pl core -am test                          # core only
mvn -Dtest=ClassName test                      # one test class
mvn -Dtest='*ExampleSamplesTest' test          # all per-template sample regressions
mvn clean verify                               # full regression + JaCoCo report

xvfb-run mvn -pl javafx-app javafx:run         # headless smoke test (CI does this)

# Generators
python3 tools/build_samples.py                 # core test samples
python3 tools/build_examples.py                # samples/tpt/*
python3 tools/build_eet_samples.py             # samples/eet/*  (also _emt_, _ept_)
python3 tools/generate_requirements.py         # rebuild requirements.md
```

Architecture, module boundaries, conventions, and the workflow for
adding a new template version are documented in
[`CONTRIBUTING.md`](CONTRIBUTING.md) and [`CLAUDE.md`](CLAUDE.md).
