# syntax=docker/dockerfile:1.7

# Base images use floating tags for now. Pinning to immutable @sha256: digests
# is desirable for supply-chain hardening but only safe once a refresh
# workflow exists (e.g. Renovate) — a stale manual pin permanently misses
# upstream security patches and is worse than a floating tag. When you adopt
# Renovate (or an equivalent), run `tools/refresh-base-images.sh` to capture
# the current digests and replace the FROM lines below with `image:tag@sha256:…`.

# ---- Stage 1: Maven build (official Maven image bundles JDK 25 + Maven 3.9) -
# Apt-Maven on Ubuntu Jammy is stuck at 3.6.3, which fails plugins that
# require Maven >= 3.9 (e.g. git-commit-id-maven-plugin v10+). The official
# `maven:3-eclipse-temurin-25-noble` image ships Maven 3.9.x preinstalled.
FROM maven:3-eclipse-temurin-26-noble AS build

WORKDIR /src

# Pre-fetch dependencies for caching: copy poms first.
COPY pom.xml .
COPY core/pom.xml core/pom.xml
COPY javafx-app/pom.xml javafx-app/pom.xml
COPY web-app/pom.xml web-app/pom.xml
RUN mvn -B -pl web-app -am dependency:go-offline -DskipTests || true

# Now copy the rest of the sources and build only what the web app needs.
COPY core core
COPY web-app web-app
# Generated per-template rules reference (docs/rules/*.md + index.json) — bundled
# into the core JAR via core/pom.xml's <resource> pointing at ../docs/rules.
# Regenerate before building the image with: mvn -pl core -Pdocs exec:java
COPY docs/rules docs/rules
# Demo files behind "Try an example" — web-app/pom.xml mounts
# samples/*/00_showcase.xlsx onto the classpath from here. Without this COPY
# Maven simply skips the missing resource directory and the image builds fine
# with the feature silently absent (SampleFiles then reports "no sample" and
# the UI hides the button), which is exactly what happened once.
COPY samples samples

# Commit identity is injected as a build-arg (CI passes ${{ github.sha }}); the
# runtime endpoint /api/build-info prefers BUILD_GIT_COMMIT/BUILD_TIME env vars
# over the git-commit-id-plugin's git.properties. We deliberately do NOT copy
# .git into the build context — even though it lives only in this throwaway
# stage, GHA layer cache (type=gha,mode=max) can persist intermediate layers
# in places that aren't always private, and shipping repo history is needless.
ARG GIT_COMMIT=
ARG BUILD_TIME=

RUN mvn -B -pl web-app -am -DskipTests package

# ---- Stage 2: jlink Custom-JRE (Alpine JDK → musl-kompatibel) ---------------
FROM eclipse-temurin:25-jdk-alpine AS jre-build

# Module set is hand-curated for Quarkus REST + Apache POI + Netty:
#   java.base/logging      core
#   java.desktop           POI fonts/imaging
#   java.management        JMX, GC monitoring
#   java.naming/.dns       JNDI + DNS resolution
#   java.net.http          j.n.h.HttpClient (used by Quarkus internals)
#   java.security.jgss/sasl, jdk.crypto.cryptoki/.ec   TLS
#   java.sql               required by transitively-loaded JDBC stubs even when unused
#   java.rmi               Agroal/SmallRye Context Propagation reference
#                          java.rmi.RemoteException at boot (usage-stats DB stack)
#   java.xml/.crypto       POI XML
#   jdk.unsupported        Netty needs sun.misc.Unsafe
#   jdk.zipfs              POI's OPC packages
#   jdk.management/.jfr    JMX + Flight Recorder
#   java.instrument        Quarkus class transformation
RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.logging,java.desktop,java.management,java.naming,java.net.http,java.rmi,java.security.jgss,java.security.sasl,java.sql,java.xml,java.xml.crypto,java.instrument,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.unsupported,jdk.zipfs,jdk.management,jdk.naming.dns,jdk.jfr,jdk.localedata \
    --include-locales=en,de \
    --strip-debug --no-man-pages --no-header-files \
    --compress=zip-6 \
    --output /custom-jre

# ---- Stage 3: GeoIP database download (build-secret gated) ------------------
# Downloads the MaxMind GeoLite2-Country.mmdb at build time. The DB is NOT
# committed (MaxMind GeoLite2 EULA forbids unattributed redistribution) and is
# never baked into a layer as plaintext credentials — the license key is a
# BuildKit secret (id=maxmind_license_key), mounted only for this RUN.
#
# No secret ⇒ empty /geoip dir; the runtime then finds no file, GeoIpService
# logs a warning and returns null (country_code stays NULL), app still boots.
# Attribution (MaxMind EULA): "This product includes GeoLite2 data created by
# MaxMind, available from https://www.maxmind.com."
FROM alpine:3.24 AS geoip
RUN apk add --no-cache curl tar
# Cache-bust: BuildKit does NOT include secret *content* in a RUN's cache key,
# so a layer built once without the license key (skip branch) would otherwise be
# reused forever even after the key is added — leaving the DB missing. Tying the
# cache key to GIT_COMMIT forces the download to re-run every commit, which also
# keeps the GeoLite2 data fresh. CI additionally sets `no-cache-filters: geoip`
# as a belt-and-suspenders guarantee (see release.yml).
ARG GIT_COMMIT=
RUN --mount=type=secret,id=maxmind_license_key \
    set -eu; \
    echo "GeoIP stage for commit '${GIT_COMMIT:-local}'"; \
    mkdir -p /geoip; \
    if [ -s /run/secrets/maxmind_license_key ]; then \
      key="$(cat /run/secrets/maxmind_license_key)"; \
      echo "GeoIP: downloading GeoLite2-Country.mmdb from MaxMind"; \
      curl -fsSL --retry 3 --retry-delay 2 \
        "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-Country&license_key=${key}&suffix=tar.gz" \
        -o /tmp/geoip.tar.gz; \
      tar -xzf /tmp/geoip.tar.gz -C /tmp; \
      mmdb="$(find /tmp -name 'GeoLite2-Country.mmdb' | head -n 1)"; \
      [ -n "$mmdb" ] || { echo "GeoIP: .mmdb not found in archive" >&2; exit 1; }; \
      cp "$mmdb" /geoip/GeoLite2-Country.mmdb; \
      rm -f /tmp/geoip.tar.gz; \
      echo "GeoIP: staged $(du -h /geoip/GeoLite2-Country.mmdb | cut -f1) database"; \
    else \
      echo "GeoIP: no maxmind_license_key build secret — skipping download (country_code will be NULL)"; \
    fi

# ---- Stage 4: Runtime (Alpine + Custom-JRE) ---------------------------------
FROM alpine:3.24

# fontconfig + ttf-dejavu: Apache POI's autoSizeColumn calls into AWT, which
# refuses to start without a font configuration on the system.
RUN apk add --no-cache tzdata ca-certificates fontconfig ttf-dejavu && \
    addgroup -S app && adduser -S -G app -u 1000 app

ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=jre-build /custom-jre $JAVA_HOME

# Pre-create the persistent cache dir owned by the runtime user. When a named
# volume is bound to /data/cache, Docker copies these perms into the fresh
# volume on first start so the container can write GLEIF/OpenFIGI lookup data.
RUN mkdir -p /data/cache && chown -R app:app /data

# GeoIP country DB for usage-stats country_code derivation (read-only at
# runtime). An empty dir here (no maxmind_license_key build secret) leaves
# FINDATEX_WEB_GEOIP_DB pointing at a missing file — GeoIpService logs a
# warning and returns null, the app still boots normally.
COPY --from=geoip --chown=app:app /geoip /data/geoip
ENV FINDATEX_WEB_GEOIP_DB=/data/geoip/GeoLite2-Country.mmdb

WORKDIR /app
COPY --from=build --chown=app:app /src/web-app/target/quarkus-app/ ./

USER 1000:1000
EXPOSE 8080

# Sensible JVM defaults for a small container.
#   MaxRAMPercentage=75 — use most of the container RAM as heap (default 25% leaves money on the table).
#   G1GC + 200ms pause — good fit for the request/response workload.
#   ExitOnOutOfMemoryError — let the supervisor restart on OOM rather than thrashing.
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError"

# Surface the build-arg values to the running JVM so BuildInfoResource can
# report them via /api/build-info. Empty values mean "no info" (the endpoint
# falls back to the bundled git.properties, which is itself empty in CI now
# that .git no longer enters the build context).
ARG GIT_COMMIT=
ARG BUILD_TIME=
ENV BUILD_GIT_COMMIT=$GIT_COMMIT
ENV BUILD_TIME=$BUILD_TIME

# `docker run` and Cloud Run callers don't see the compose-level healthcheck;
# baking it into the image ensures liveness regardless of the launcher.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/_internal/health/ready || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS_APPEND -jar quarkus-run.jar"]
