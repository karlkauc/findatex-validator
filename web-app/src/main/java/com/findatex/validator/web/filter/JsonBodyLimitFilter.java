package com.findatex.validator.web.filter;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Small-body cap for the JSON POST endpoints (usage stats, page view, quick
 * feedback, newsletter). {@code quarkus.http.limits.max-body-size} is global and
 * sized for the 25 MB upload on {@code /api/validate}; without this filter every
 * one-line JSON endpoint would buffer and parse 25 MB for anyone who asks.
 *
 * <p>Rejected before the body is read: a declared {@code Content-Length} above
 * {@link #MAX_BYTES} gets 413, a body without one (chunked) gets 411 — every
 * legitimate client (fetch, sendBeacon, the desktop's {@code HttpClient}) sends
 * a fixed length.
 */
@ApplicationScoped
public class JsonBodyLimitFilter {

    static final long MAX_BYTES = 64 * 1024;

    /** Ahead of the body handler and the REST layer. */
    private static final int PRIORITY = 300;

    void register(@Observes Filters filters) {
        filters.register(JsonBodyLimitFilter::check, PRIORITY);
    }

    private static void check(RoutingContext rc) {
        if (rc.request().method() != HttpMethod.POST || !isJsonEndpoint(rc.normalizedPath())) {
            rc.next();
            return;
        }
        String length = rc.request().getHeader("Content-Length");
        if (length == null) {
            if (rc.request().getHeader("Transfer-Encoding") != null) {
                rc.response().setStatusCode(411).end();
                return;
            }
        } else {
            long n;
            try {
                n = Long.parseLong(length.trim());
            } catch (NumberFormatException e) {
                n = Long.MAX_VALUE;
            }
            if (n > MAX_BYTES) {
                rc.response().setStatusCode(413).end();
                return;
            }
        }
        rc.next();
    }

    static boolean isJsonEndpoint(String path) {
        if (path == null) return false;
        return path.startsWith("/api/usage-stats")
                || path.startsWith("/api/page-view")
                || path.startsWith("/api/quick-feedback")
                || path.startsWith("/api/newsletter");
    }
}
