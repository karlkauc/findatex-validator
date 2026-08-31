package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Builds the absolute URLs that go into {@code <link rel="canonical">} and the
 * sitemap.
 *
 * <p>When a canonical host is configured it always wins, so a page reachable on
 * the apex domain or the {@code *.run.app} URL still declares the one URL that
 * should be indexed. Self-hosted instances configure nothing and get their own
 * hostname from the request instead of this deployment's.
 */
@ApplicationScoped
public class PublicUrls {

    @Inject
    WebConfig config;

    /** Scheme + host, no trailing slash. */
    public String origin(HttpServerRequest request) {
        String canonical = config.canonicalHost().orElse(null);
        if (canonical != null) return "https://" + canonical;

        String host = request == null ? null : hostOf(request);
        if (host == null || host.isBlank()) return "";
        // Behind Cloud Run / a reverse proxy the inbound hop is plain HTTP, so
        // request.isSSL() would understate it; anything that is not a local
        // hostname is served over TLS in practice.
        boolean local = host.startsWith("localhost") || host.startsWith("127.0.0.1");
        String port = request.authority() != null && request.authority().port() > 0
                ? ":" + request.authority().port() : "";
        return (local ? "http://" : "https://") + host + (local ? port : "");
    }

    public String absolute(HttpServerRequest request, String path) {
        return origin(request) + path;
    }

    private static String hostOf(HttpServerRequest request) {
        if (request.authority() != null && request.authority().host() != null) {
            return request.authority().host();
        }
        String header = request.getHeader("Host");
        if (header == null) return null;
        int colon = header.lastIndexOf(':');
        return colon > 0 ? header.substring(0, colon) : header;
    }
}
