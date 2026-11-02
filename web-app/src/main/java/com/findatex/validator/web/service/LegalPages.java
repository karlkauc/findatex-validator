package com.findatex.validator.web.service;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves the imprint and the privacy policy from {@code resources/legal/*.md}.
 * Keep {@code privacy.md} true: it states publicly what the site collects.
 */
@ApplicationScoped
public class LegalPages {

    private static final String CACHE = "public, max-age=3600";

    @Inject
    RulesPageRenderer renderer;

    @Inject
    PublicUrls urls;

    public Response render(HttpServerRequest request, String name, String title, String description) {
        String path = "/" + name;
        return Response.ok(renderer.legalPage(
                        "FinDatEx Validator — " + title,
                        description,
                        urls.absolute(request, path),
                        "<a href=\"/\">Validator</a> › " + title,
                        renderer.proseToHtml(markdown(name))), MediaType.TEXT_HTML_TYPE)
                .header("Cache-Control", CACHE)
                .build();
    }

    private static String markdown(String name) {
        String resource = "legal/" + name + ".md";
        try (InputStream in = LegalPages.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("missing resource " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
