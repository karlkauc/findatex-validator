package com.findatex.validator.web.filter;

import com.findatex.validator.web.config.WebConfig;
import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Redirects every GET/HEAD that arrives on a non-canonical hostname to the
 * configured canonical one with a 301.
 *
 * <p>Why: the service answers on the apex domain, the {@code www} subdomain and
 * its {@code *.run.app} Cloud Run URL with byte-identical HTML. To a search
 * engine that is three copies of the same site, and the ranking signals split
 * across them. One 301 (plus the {@code <link rel="canonical">} in index.html)
 * collapses them into one.
 *
 * <p>This is a Vert.x route filter, not a JAX-RS {@code ContainerRequestFilter}:
 * the page that matters most ({@code /}) is served by the static-resource
 * handler and never reaches the JAX-RS layer.
 *
 * <p>Off by default ({@code findatex.web.canonical-host} empty) — no filter is
 * registered at all, so dev mode, tests and self-hosted deployments on any
 * hostname are unaffected. Production sets
 * {@code FINDATEX_WEB_CANONICAL_HOST=www.findatex-validator.eu}.
 *
 * <p>Deliberately narrow:
 * <ul>
 *   <li><b>GET/HEAD only</b> — a 301 on POST is rewritten to GET by browsers,
 *       which would silently drop an upload.</li>
 *   <li><b>{@code /_internal/} excluded</b> — health probes hit the service on
 *       its internal hostname and must not be bounced.</li>
 * </ul>
 * The redirect target host is the configured constant, never a request header,
 * so no user-supplied value can turn this into an open redirect.
 */
@ApplicationScoped
public class CanonicalHostFilter {

    private static final Logger LOG = Logger.getLogger(CanonicalHostFilter.class);

    /** Runs before the static-resource and JAX-RS routes (higher = earlier). */
    private static final int PRIORITY = 300;

    @Inject
    WebConfig config;

    void register(@Observes Filters filters) {
        Optional<String> canonical = config.canonicalHost();
        if (canonical.isEmpty()) return;

        String target = canonical.get();
        LOG.infof("Canonical host redirect enabled: 301 to https://%s", target);
        filters.register(rc -> redirectIfForeignHost(rc, target), PRIORITY);
    }

    private static void redirectIfForeignHost(RoutingContext rc, String target) {
        HttpServerRequest request = rc.request();
        String method = request.method().name();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            rc.next();
            return;
        }

        String path = request.path();
        if (path != null && path.startsWith("/_internal/")) {
            rc.next();
            return;
        }

        String host = hostOf(request);
        if (host == null || host.equalsIgnoreCase(target)) {
            rc.next();
            return;
        }

        String query = request.query();
        String location = "https://" + target
                + (path == null || path.isEmpty() ? "/" : path)
                + (query == null || query.isEmpty() ? "" : "?" + query);
        rc.response()
                .setStatusCode(301)
                .putHeader("Location", location)
                .putHeader("Cache-Control", "public, max-age=3600")
                .end();
    }

    /**
     * Requested hostname without the port. Uses the parsed authority (works for
     * HTTP/2, where there is no {@code Host} header) and falls back to the
     * header for HTTP/1.1 clients that Vert.x could not parse.
     */
    private static String hostOf(HttpServerRequest request) {
        HostAndPort authority = request.authority();
        if (authority != null && authority.host() != null && !authority.host().isBlank()) {
            return authority.host();
        }
        String header = request.getHeader("Host");
        if (header == null || header.isBlank()) return null;
        int colon = header.lastIndexOf(':');
        return colon > 0 ? header.substring(0, colon) : header;
    }
}
