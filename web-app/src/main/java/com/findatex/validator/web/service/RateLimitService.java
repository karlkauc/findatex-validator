package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
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
 * {@code 3600 / capacity} seconds.
 *
 * <p><b>IP source.</b> The bucket key is the TCP-level source address of the inbound
 * request (the value Quarkus exposes via {@code HttpServerRequest.remoteAddress()}).
 * Client-supplied headers like {@code X-Forwarded-For} / {@code X-Real-IP} are
 * <i>not</i> read by this class — they are trivially forgeable when the service is
 * exposed without a sanitising reverse proxy, and would let an unauthenticated
 * attacker mint a fresh bucket per spoofed value.
 *
 * <p>Operators behind a real reverse proxy who want per-original-client buckets
 * must opt in by setting {@code quarkus.http.proxy.proxy-address-forwarding=true}
 * <b>and</b> {@code quarkus.http.proxy.trusted-proxies=<LB-CIDR>} in
 * {@code application.properties}; Quarkus then rewrites {@code remoteAddress()}
 * itself, only honouring forwarded headers from the listed CIDRs.
 */
@ApplicationScoped
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final int WINDOW_SECONDS = 3600;

    @Inject
    WebConfig config;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> usageBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> newsletterBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> quickFeedbackBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> pageViewBuckets = new ConcurrentHashMap<>();
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
        return usageBuckets.computeIfAbsent(normaliseKey(clientIp),
                k -> Bucket.builder().addLimit(Bandwidth.builder()
                        .capacity(usageCapacityPerHour)
                        .refillIntervally(1, usageRefillInterval)
                        .build()).build())
                .tryConsumeAndReturnRemaining(1);
    }

    /** Separate, strict bucket for newsletter sign-up (anti e-mail-bombing). */
    public ConsumptionProbe consumeNewsletter(String clientIp) {
        return newsletterBuckets.computeIfAbsent(normaliseKey(clientIp),
                k -> Bucket.builder().addLimit(Bandwidth.builder()
                        .capacity(newsletterCapacityPerHour)
                        .refillIntervally(1, newsletterRefillInterval)
                        .build()).build())
                .tryConsumeAndReturnRemaining(1);
    }

    /** Separate, strict bucket for quick-feedback submissions (anti-spam). */
    public ConsumptionProbe consumeQuickFeedback(String clientIp) {
        return quickFeedbackBuckets.computeIfAbsent(normaliseKey(clientIp),
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
        return pageViewBuckets.computeIfAbsent(normaliseKey(clientIp),
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

    public RateLimitStatus inspect(String clientIp) {
        Bucket bucket = bucketFor(clientIp);
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
        return buckets.computeIfAbsent(normaliseKey(clientIp), k -> newBucket());
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacityPerHour)
                .refillIntervally(1, refillInterval)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    String normaliseKey(String clientIp) {
        if (clientIp != null && !clientIp.isBlank()) {
            return clientIp.trim();
        }
        if (unknownWarned.compareAndSet(false, true)) {
            log.warn("No TCP source address on incoming request — all such traffic shares "
                    + "one rate-limit bucket. This indicates a Quarkus/Vert.x configuration "
                    + "issue; per-IP throttling is effectively disabled until fixed.");
        }
        return "unknown";
    }
}
