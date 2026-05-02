package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the per-IP token-bucket state used to rate-limit {@code POST /api/validate}.
 * Centralised so both the request filter (consuming a token) and the read-only
 * status endpoint (inspecting a bucket without consuming) share one bucket map.
 *
 * <p>Capacity = {@link WebConfig.RateLimit#perIpPerHour()}; refills 1 token every
 * {@code 3600 / capacity} seconds. The bucket map is keyed by the client IP that
 * a trusted reverse proxy reports — never by client-controllable headers.
 */
@ApplicationScoped
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final int WINDOW_SECONDS = 3600;

    @Inject
    WebConfig config;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicBoolean unknownWarned = new AtomicBoolean(false);
    private int capacityPerHour;
    private Duration refillInterval;

    @PostConstruct
    void init() {
        capacityPerHour = Math.max(1, config.rateLimit().perIpPerHour());
        long refillSeconds = Math.max(1, 3600L / capacityPerHour);
        refillInterval = Duration.ofSeconds(refillSeconds);
        log.info("Rate limit configured: {} req/hour per IP (1 token every {}s)",
                capacityPerHour, refillSeconds);
    }

    public int limit() {
        return capacityPerHour;
    }

    public int windowSeconds() {
        return WINDOW_SECONDS;
    }

    public ConsumptionProbe consume(String forwardedFor, String realIp) {
        return bucketFor(forwardedFor, realIp).tryConsumeAndReturnRemaining(1);
    }

    public RateLimitStatus inspect(String forwardedFor, String realIp) {
        Bucket bucket = bucketFor(forwardedFor, realIp);
        long remaining = bucket.getAvailableTokens();
        long resetInSeconds = 0L;
        if (remaining < capacityPerHour) {
            long nanosToFullRefill = bucket.estimateAbilityToConsume(capacityPerHour)
                    .getNanosToWaitForRefill();
            resetInSeconds = Math.max(0L, nanosToFullRefill / 1_000_000_000L);
        }
        return new RateLimitStatus(capacityPerHour, remaining, WINDOW_SECONDS, resetInSeconds);
    }

    private Bucket bucketFor(String forwardedFor, String realIp) {
        return buckets.computeIfAbsent(clientIp(forwardedFor, realIp), k -> newBucket());
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(
                capacityPerHour,
                Refill.intervally(1, refillInterval));
        return Bucket.builder().addLimit(limit).build();
    }

    String clientIp(String forwardedFor, String realIp) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Rightmost entry: appended by our trusted reverse proxy (the TCP source it
            // actually saw). Earlier entries are client-controllable and would let an
            // attacker mint a fresh bucket per spoofed value.
            int comma = forwardedFor.lastIndexOf(',');
            return (comma >= 0 ? forwardedFor.substring(comma + 1) : forwardedFor).trim();
        }
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        if (unknownWarned.compareAndSet(false, true)) {
            log.warn("No X-Forwarded-For/X-Real-IP on incoming request — all such traffic "
                    + "shares one rate-limit bucket. If you're in production, ensure your "
                    + "reverse proxy sets these headers.");
        }
        return "unknown";
    }
}
