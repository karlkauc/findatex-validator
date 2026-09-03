package com.findatex.validator.web.service;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single place that looks at the request's source IP and User-Agent for
 * statistics purposes. Every resource used to carry its own {@code clientIp()}
 * copy; now they all ask here. The IP is used for the GeoIP lookup, the
 * visitor hash and the rate-limit bucket key — and is never stored or logged.
 *
 * <p>{@link HttpServerRequest#remoteAddress()} is the TCP peer unless Quarkus'
 * proxy-address-forwarding is switched on with a trusted-proxies list (see
 * application.properties) — same rule as before.
 */
@ApplicationScoped
public class ClientContextFactory {

    private static final Logger log = LoggerFactory.getLogger(ClientContextFactory.class);

    @Inject
    GeoIpService geoIp;

    @Inject
    VisitorHasher hasher;

    /** Raw source IP for rate limiting; may be null (tests, odd transports). */
    public static String clientIp(HttpServerRequest request) {
        if (request == null || request.remoteAddress() == null) return null;
        return request.remoteAddress().host();
    }

    public ClientContext from(HttpServerRequest request) {
        String ip = clientIp(request);
        String ua = request == null ? null : request.getHeader("User-Agent");
        String country = null;
        try {
            country = geoIp.countryFor(ip);
        } catch (RuntimeException e) {
            log.debug("Geo lookup failed (ignored)");
        }
        String hash = null;
        try {
            hash = hasher.hash(ip, ua);
        } catch (RuntimeException e) {
            log.debug("Visitor hash failed (ignored)");
        }
        return new ClientContext(country, hash, UserAgentClassifier.truncate(ua),
                UserAgentClassifier.device(ua), UserAgentClassifier.osFamily(ua));
    }
}
