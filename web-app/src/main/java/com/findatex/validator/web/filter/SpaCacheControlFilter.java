package com.findatex.validator.web.filter;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Stops the SPA shell from being cached like an immutable asset.
 *
 * <p>Quarkus serves everything under {@code META-INF/resources/} with
 * {@code public, immutable, max-age=86400}. That is exactly right for
 * {@code /assets/index-<hash>.js}, whose name changes with its content, and
 * exactly wrong for {@code index.html}, whose name never changes but whose
 * content points at those hashed files: a returning visitor keeps the previous
 * shell — and therefore the previous bundle — for up to a day after a deploy,
 * and gets a blank page once the old assets are gone.
 *
 * <p>The header is rewritten in a headers-end handler because the static
 * handler sets it while writing the response, after this filter has run.
 * {@code SpaFallbackResource} already sets {@code no-cache} for client-side
 * routes; this covers the two paths that reach the static handler instead.
 */
@ApplicationScoped
public class SpaCacheControlFilter {

    /** Ahead of the static-resource route, like the canonical-host redirect. */
    private static final int PRIORITY = 290;

    private static final String NO_CACHE = "no-cache";

    void register(@Observes Filters filters) {
        filters.register(SpaCacheControlFilter::markShellUncacheable, PRIORITY);
    }

    private static void markShellUncacheable(RoutingContext rc) {
        String path = rc.request().path();
        if (isShell(path)) {
            rc.addHeadersEndHandler(v -> rc.response().putHeader("Cache-Control", NO_CACHE));
        }
        rc.next();
    }

    private static boolean isShell(String path) {
        return "/".equals(path) || "/index.html".equals(path);
    }
}
