package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single place that looks at the request's source IP and User-Agent for
 * statistics purposes. Every resource used to carry its own {@code clientIp()}
 * copy; now they all ask here. The IP is used for the GeoIP lookup, the
 * visitor hash and the rate-limit bucket key — and is never stored or logged.
 *
 * <p>Source of the IP: {@link HttpServerRequest#remoteAddress()} (the TCP peer,
 * or what Quarkus' proxy-address-forwarding rewrote it to when switched on with a
 * trusted-proxies list) unless {@code findatex.web.client-ip.forwarded-hops} is
 * &gt; 0. Then the N-th {@code X-Forwarded-For} entry <i>from the right</i> is
 * used — the left part of that header is whatever the client sent and is never
 * trusted. The result must be an IP literal; anything else (hostname, garbage,
 * too few entries) falls back to the TCP peer, so no value can trigger DNS.
 */
@ApplicationScoped
public class ClientContextFactory {

    private static final Logger log = LoggerFactory.getLogger(ClientContextFactory.class);

    @Inject
    GeoIpService geoIp;

    @Inject
    VisitorHasher hasher;

    @Inject
    WebConfig config;

    private final AtomicBoolean shapeLogged = new AtomicBoolean(false);

    /** Source IP for rate limiting / GeoIP / visitor hash; may be null (tests, odd transports). */
    public String clientIp(HttpServerRequest request) {
        if (request == null) return null;
        String peer = request.remoteAddress() == null ? null : request.remoteAddress().host();
        int hops = config.clientIpForwardedHops();
        if (hops == 0) return peer;
        List<String> headers = request.headers().getAll("X-Forwarded-For");
        if (shapeLogged.compareAndSet(false, true)) {
            // Entry count only — never the addresses. Lets an operator confirm the
            // forwarded-hops setting against what the platform actually sends.
            log.info("X-Forwarded-For on first request: {} header(s), {} entr(ies); forwarded-hops={}",
                    headers.size(), String.join(",", headers).split(",", -1).length, hops);
        }
        return resolve(peer, headers, hops);
    }

    /**
     * Picks the {@code hops}-th entry from the right of the combined
     * {@code X-Forwarded-For} values; falls back to {@code peer} when that entry
     * is missing or not an IP literal.
     */
    static String resolve(String peer, List<String> forwardedFor, int hops) {
        if (hops <= 0 || forwardedFor == null || forwardedFor.isEmpty()) return peer;
        String[] entries = String.join(",", forwardedFor).split(",");
        int idx = entries.length - hops;
        if (idx < 0) return peer;
        return IpLiterals.normalise(entries[idx]).orElse(peer);
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
