package com.findatex.validator.web.api;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves the canonical end-user help document. The Markdown source lives in
 * {@code core/src/main/resources/help/HELP.md} and is shared with the JavaFX desktop UI.
 * Loaded once at startup and held in memory — there is exactly one HELP.md per build.
 */
@ApplicationScoped
@Path("/api/help")
public class HelpResource {

    private static final String RESOURCE_PATH = "/help/HELP.md";

    private String body;

    @PostConstruct
    void load() {
        try (InputStream in = HelpResource.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE_PATH);
            }
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + RESOURCE_PATH, e);
        }
    }

    @GET
    @Produces("text/markdown; charset=utf-8")
    public Response get() {
        if (body == null) throw new InternalServerErrorException("Help content not loaded");
        return Response.ok(body)
                .header("Cache-Control", "public, max-age=3600")
                .build();
    }
}
