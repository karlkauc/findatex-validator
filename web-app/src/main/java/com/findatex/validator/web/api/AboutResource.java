package com.findatex.validator.web.api;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves the end-user About document (author, project license, third-party libraries).
 * The Markdown source lives in {@code web-app/src/main/resources/about/ABOUT.md} and
 * is web-bundle specific — it lists the libraries that ship with the container, which
 * differs from the desktop bundle. Loaded once at startup and held in memory.
 */
@ApplicationScoped
@Path("/api/about")
public class AboutResource {

    private static final String RESOURCE_PATH = "/about/ABOUT.md";

    /** Placeholder in ABOUT.md replaced with the Maven version at startup. */
    static final String VERSION_TOKEN = "{{version}}";

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    private String body;

    @PostConstruct
    void load() {
        try (InputStream in = AboutResource.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE_PATH);
            }
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace(VERSION_TOKEN, version == null || version.isBlank() ? "dev" : version);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + RESOURCE_PATH, e);
        }
    }

    @GET
    @Produces("text/markdown; charset=utf-8")
    public Response get() {
        if (body == null) throw new InternalServerErrorException("About content not loaded");
        return Response.ok(body)
                .header("Cache-Control", "public, max-age=3600")
                .build();
    }
}
