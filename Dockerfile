# syntax=docker/dockerfile:1.7

# ---- Stage 1: Maven build (Ubuntu, has apt+maven prebuilt) ------------------
FROM eclipse-temurin:25-jdk-jammy AS build

RUN apt-get update \
 && apt-get install -y --no-install-recommends maven ca-certificates \
 && rm -rf /var/lib/apt/lists/*

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
FROM alpine:3.20

# fontconfig + ttf-dejavu: Apache POI's autoSizeColumn calls into AWT, which
# refuses to start without a font configuration on the system.
RUN apk add --no-cache tzdata ca-certificates fontconfig ttf-dejavu && \
    addgroup -S app && adduser -S -G app -u 1000 app

ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=jre-build /custom-jre $JAVA_HOME

WORKDIR /app
COPY --from=build --chown=app:app /src/web-app/target/quarkus-app/ ./

USER 1000:1000
EXPOSE 8080

# Sensible JVM defaults for a small container.
#   MaxRAMPercentage=75 — use most of the container RAM as heap (default 25% leaves money on the table).
#   G1GC + 200ms pause — good fit for the request/response workload.
#   ExitOnOutOfMemoryError — let the supervisor restart on OOM rather than thrashing.
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS_APPEND -jar quarkus-run.jar"]
