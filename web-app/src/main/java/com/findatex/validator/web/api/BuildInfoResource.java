package com.findatex.validator.web.api;

import com.findatex.validator.web.dto.BuildInfo;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reports the running container's identity (Maven version + git metadata).
 *
 * <p>The git fields come from the classpath resource {@code /git.properties}, written at build
 * time by {@code git-commit-id-maven-plugin}. The file is parsed once per JVM and the result
 * cached — the values cannot change at runtime. When the file is missing or unreadable the git
 * fields fall back to empty strings / {@code false}; the version field always returns the Maven
 * coordinate Quarkus injected from the POM.
 */
@ApplicationScoped
@Path("/api/build-info")
public class BuildInfoResource {

    private static final String GIT_PROPERTIES = "/git.properties";

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "")
    String version;

    private BuildInfo cached;

    @PostConstruct
    void init() {
        Properties p = new Properties();
        try (InputStream in = BuildInfoResource.class.getResourceAsStream(GIT_PROPERTIES)) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignored) {
            // Treat as "no git info" — log nothing; the endpoint will report empty fields.
        }
        // Container builds inject the commit / build time via env vars
        // (BUILD_GIT_COMMIT, BUILD_TIME) because the .git directory is no longer
        // copied into the build context. Local mvn builds still get values from
        // git.properties. Env wins when set so the runtime image matches what CI
        // recorded for that exact build.
        String commit = firstNonEmpty(System.getenv("BUILD_GIT_COMMIT"),
                p.getProperty("git.commit.id.abbrev", ""));
        if (commit.length() > 7) commit = commit.substring(0, 7);
        boolean dirty = Boolean.parseBoolean(p.getProperty("git.dirty", "false"));
        String buildTime = firstNonEmpty(System.getenv("BUILD_TIME"),
                p.getProperty("git.build.time", ""));
        cached = new BuildInfo(version == null ? "" : version, commit, dirty, buildTime);
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null ? "" : b;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public BuildInfo get() {
        return cached;
    }
}
