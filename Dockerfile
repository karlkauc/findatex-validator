# syntax=docker/dockerfile:1.7

# ---- Stage 1: build (JDK + Maven, downloads Node via frontend-maven-plugin) -------
FROM eclipse-temurin:21-jdk AS build

# Maven from a stable image-baked apt package keeps the build reproducible.
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

# ---- Stage 2: runtime (JRE only — no JavaFX, no Maven, no npm) -------------------
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Copy the Quarkus fast-jar layout (already includes /app/lib).
COPY --from=build /src/web-app/target/quarkus-app/ ./

EXPOSE 8080
USER 1000:1000

# Sensible JVM defaults for a small container.
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS_APPEND -jar quarkus-run.jar"]
