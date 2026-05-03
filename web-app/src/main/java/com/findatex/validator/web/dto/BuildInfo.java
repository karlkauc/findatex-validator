package com.findatex.validator.web.dto;

/**
 * Identity of the running web container. Returned by {@code GET /api/build-info}.
 *
 * <p>{@code version} comes from Maven (Quarkus auto-populates {@code quarkus.application.version}
 * from the POM). {@code commit}, {@code dirty}, and {@code buildTime} are read from the classpath
 * {@code /git.properties} produced by {@code git-commit-id-maven-plugin}; when that file is absent
 * (e.g., a build outside a git checkout), those three fields are empty / {@code false}.
 */
public record BuildInfo(String version, String commit, boolean dirty, String buildTime) {
}
