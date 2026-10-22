package com.findatex.validator.web.filter;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Keeps {@code window.opener} alive on the SPA shell so a sibling site can hand
 * over a file (see {@code lib/handoff.ts}).
 *
 * <p>The global {@code Cross-Origin-Opener-Policy: same-origin} puts a page
 * opened from another origin into its own browsing-context group: the opener
 * becomes {@code null} and neither side can {@code postMessage} the other. The
 * TPT converter opens {@code /?from=tpt-converter} and posts the generated file
 * once this page announces it is ready, so the shell gets {@code unsafe-none}.
 * Everything else, the API included, keeps {@code same-origin}. Origins are
 * checked on both ends of the message exchange, and {@code frame-ancestors
 * 'none'} still forbids embedding.
 *
 * <p>Rewritten in a headers-end handler, like {@link SpaCacheControlFilter},
 * because the configured header is added after this filter has run.
 */
@ApplicationScoped
public class SpaOpenerPolicyFilter {

    /** Ahead of the static-resource route, like the canonical-host redirect. */
    private static final int PRIORITY = 290;

    private static final String HEADER = "Cross-Origin-Opener-Policy";

    void register(@Observes Filters filters) {
        filters.register(SpaOpenerPolicyFilter::keepOpener, PRIORITY);
    }

    private static void keepOpener(RoutingContext rc) {
        String path = rc.request().path();
        if ("/".equals(path) || "/index.html".equals(path)) {
            rc.addHeadersEndHandler(v -> rc.response().putHeader(HEADER, "unsafe-none"));
        }
        rc.next();
    }
}
