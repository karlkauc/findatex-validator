package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the per-IP token-bucket state used to rate-limit {@code POST /api/validate}.
 * Centralised so both the request filter (consuming a token) and the read-only
 * status endpoint (inspecting a bucket without consuming) share one bucket map.
 *
 * <p>Capacity = {@link WebConfig.RateLimit#perIpPerHour()}; refills 1 token every
 * {@code 3600 / capacity} seconds.
 *
 * <p><b>IP source.</b> Callers pass the address resolved by
 * {@link ClientContextFactory#clientIp} — the TCP peer, or the trusted right-most
 * {@code X-Forwarded-For} hop when {@code findatex.web.client-ip.forwarded-hops}
 * is set. The client-controlled left part of that header is never read, so a
 * spoofed value cannot mint a fresh bucket.
 *
 * <p><b>Memory bound.</b> Every bucket map is a Caffeine cache that forgets a key
 * after {@link #IDLE_EXPIRY} without traffic (by then the bucket has fully
 * refilled anyway, so forgetting it changes nothing) and holds at most
 * {@link #MAX_KEYS} keys. Keys are IP literals (IPv6 collapsed to its /64); any
 * other value shares the single {@code unknown} bucket.
 */
@ApplicationScoped
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final int WINDOW_SECONDS = 3600;
    /** Longer than the 1 h window: an idle bucket is full again long before it is dropped. */
    static final Duration IDLE_EXPIRY = Duration.ofHours(2);
    static final long MAX_KEYS = 100_000;

    @Inject
    WebConfig config;

    private final Cache<String, Bucket> buckets = newCache();
    private final Cache<String, Bucket> usageBuckets = newCache();
    private final Cache<String, Bucket> newsletterBuckets = newCache();
    private final Cache<String, Bucket> quickFeedbackBuckets = newCache();
    private final Cache<String, Bucket> pageViewBuckets = newCache();
    private final AtomicBoolean unknownWarned = new AtomicBoolean(false);
    private int capacityPerHour;
    private Duration refillInterval;
    private int usageCapacityPerHour;
    private Duration usageRefillInterval;
    private int newsletterCapacityPerHour;
    private Duration newsletterRefillInterval;
    private int quickFeedbackCapacityPerHour;
    private Duration quickFeedbackRefillInterval;
    private int pageViewCapacityPerHour;
    private Duration pageViewRefillInterval;

    @PostConstruct
    void init() {
        capacityPerHour = Math.max(1, config.rateLimit().perIpPerHour());
        long refillSeconds = Math.max(1, 3600L / capacityPerHour);
        refillInterval = Duration.ofSeconds(refillSeconds);
        usageCapacityPerHour = Math.max(1, config.usageStats().ratePerIpPerHour());
        long usageRefillSeconds = Math.max(1, 3600L / usageCapacityPerHour);
        usageRefillInterval = Duration.ofSeconds(usageRefillSeconds);
        newsletterCapacityPerHour = Math.max(1, config.newsletter().ratePerIpPerHour());
        long newsletterRefillSeconds = Math.max(1, 3600L / newsletterCapacityPerHour);
        newsletterRefillInterval = Duration.ofSeconds(newsletterRefillSeconds);
        quickFeedbackCapacityPerHour = Math.max(1, config.quickFeedback().ratePerIpPerHour());
        long quickFeedbackRefillSeconds = Math.max(1, 3600L / quickFeedbackCapacityPerHour);
        quickFeedbackRefillInterval = Duration.ofSeconds(quickFeedbackRefillSeconds);
        pageViewCapacityPerHour = Math.max(1, config.pageView().ratePerIpPerHour());
        long pageViewRefillSeconds = Math.max(1, 3600L / pageViewCapacityPerHour);
        pageViewRefillInterval = Duration.ofSeconds(pageViewRefillSeconds);
        log.info("Rate limit configured: {} req/hour per IP for /api/validate, "
                        + "{} req/hour per IP for /api/usage-stats, "
                        + "{} req/hour per IP for /api/newsletter, "
                        + "{} req/hour per IP for /api/quick-feedback, "
                        + "{} req/hour per IP for /api/page-view",
                capacityPerHour, usageCapacityPerHour, newsletterCapacityPerHour,
                quickFeedbackCapacityPerHour, pageViewCapacityPerHour);
    }

    /** Separate, more generous bucket for the lightweight usage-stats ingest. */
    public ConsumptionProbe consumeUsage(String clientIp) {
        return usageBuckets.get(normaliseKey(clientIp),
                k -> Bucket.builder().addLimit(Bandwidth.builder()
                        .capacity(usageCapacityPerHour)
                        .refillIntervally(1, usageRefillInterval)
                        .build()).build())
                .tryConsumeAndReturnRemaining(1);
    }

    /** Separate, strict bucket for newsletter sign-up (anti e-mail-bombing). */
    public ConsumptionProbe consumeNewsletter(String clientIp) {
        return newsletterBuckets.get(normaliseKey(clientIp),
                k -> Bucket.builder().addLimit(Bandwidth.builder()
                        .capacity(newsletterCapacityPerHour)
                        .refillIntervally(1, newsletterRefillInterval)
                        .build()).build())
                .tryConsumeAndReturnRemaining(1);
    }

    /** Separate, strict bucket for quick-feedback submissions (anti-spam). */
    public ConsumptionProbe consumeQuickFeedback(String clientIp) {
        return quickFeedbackBuckets.get(normaliseKey(clientIp),
                k -> Bucket.builder().addLimit(Bandwidth.builder()
                        .capacity(quickFeedbackCapacityPerHour)
                        .refillIntervally(1, quickFeedbackRefillInterval)
                        .build()).build())
                .tryConsumeAndReturnRemaining(1);
    }

    /**
     * Separate, generous bucket for the page-view beacon: a real visitor
     * reloading or opening tabs must never be throttled, but the counter still
     * has to be expensive to inflate.
     */
    public ConsumptionProbe consumePageView(String clientIp) {
        return pageViewBuckets.get(normaliseKey(clientIp),
                k -> Bucket.builder().addLimit(Bandwidth.builder()
                        .capacity(pageViewCapacityPerHour)
                        .refillIntervally(1, pageViewRefillInterval)
                        .build()).build())
                .tryConsumeAndReturnRemaining(1);
    }

    public int limit() {
        return capacityPerHour;
    }

    public int windowSeconds() {
        return WINDOW_SECONDS;
    }

    public ConsumptionProbe consume(String clientIp) {
        return bucketFor(clientIp).tryConsumeAndReturnRemaining(1);
    }

    /** Read-only: an address without a bucket has its full quota and gets none created. */
    public RateLimitStatus inspect(String clientIp) {
        Bucket bucket = buckets.getIfPresent(normaliseKey(clientIp));
        if (bucket == null) {
            return new RateLimitStatus(capacityPerHour, capacityPerHour, WINDOW_SECONDS, 0L);
        }
        long remaining = bucket.getAvailableTokens();
        long resetInSeconds = 0L;
        if (remaining < capacityPerHour) {
            long nanosToFullRefill = bucket.estimateAbilityToConsume(capacityPerHour)
                    .getNanosToWaitForRefill();
            resetInSeconds = Math.max(0L, nanosToFullRefill / 1_000_000_000L);
        }
        return new RateLimitStatus(capacityPerHour, remaining, WINDOW_SECONDS, resetInSeconds);
    }

    private Bucket bucketFor(String clientIp) {
        return buckets.get(normaliseKey(clientIp), k -> newBucket());
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacityPerHour)
                .refillIntervally(1, refillInterval)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    String normaliseKey(String clientIp) {
        String key = IpLiterals.rateLimitKey(clientIp).orElse(null);
        if (key != null) {
            return key;
        }
        if (unknownWarned.compareAndSet(false, true)) {
            log.warn("Request without a usable source IP address — all such traffic shares "
                    + "one rate-limit bucket. This indicates a Quarkus/Vert.x configuration "
                    + "issue; per-IP throttling is effectively disabled until fixed.");
        }
        return "unknown";
    }

    long trackedKeys() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }

    private static Cache<String, Bucket> newCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(IDLE_EXPIRY)
                .maximumSize(MAX_KEYS)
                .build();
    }
}
