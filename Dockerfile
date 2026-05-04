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
FROM maven:3-eclipse-temurin-25-noble AS build

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
#   java.xml/.crypto       POI XML
#   jdk.unsupported        Netty needs sun.misc.Unsafe
#   jdk.zipfs              POI's OPC packages
#   jdk.management/.jfr    JMX + Flight Recorder
#   java.instrument        Quarkus class transformation
RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.logging,java.desktop,java.management,java.naming,java.net.http,java.security.jgss,java.security.sasl,java.sql,java.xml,java.xml.crypto,java.instrument,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.unsupported,jdk.zipfs,jdk.management,jdk.naming.dns,jdk.jfr,jdk.localedata \
    --include-locales=en,de \
    --strip-debug --no-man-pages --no-header-files \
    --compress=zip-6 \
    --output /custom-jre

# ---- Stage 3: Runtime (Alpine + Custom-JRE) ---------------------------------
FROM alpine:3.23

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
