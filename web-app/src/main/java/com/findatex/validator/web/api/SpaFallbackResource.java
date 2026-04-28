package com.findatex.validator.web.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;

/**
 * Single-page-application fallback: any non-/api path that didn't match a
 * static resource gets the SPA's {@code index.html} so client-side routing
 * (e.g. /findings, /upload) works on full-page reloads.
 *
 * <p>Quarkus serves META-INF/resources/ as the document root automatically,
 * so plain GET /, /assets/foo.js, /favicon.ico already work. This resource
 * only kicks in for paths that don't match any static asset.
 */
@Path("/")
public class SpaFallbackResource {

    @GET
    @Path("/{path:(?!api/|q/|_internal/).+}")
    @Produces(MediaType.TEXT_HTML)
    public Response fallback(@PathParam("path") String path) {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/resources/index.html")) {
            if (in == null) throw new NotFoundException();
            byte[] bytes = in.readAllBytes();
            return Response.ok(bytes, MediaType.TEXT_HTML)
                    .header("Cache-Control", "no-cache")
                    .build();
        } catch (IOException e) {
            throw new NotFoundException();
        }
    }
}
