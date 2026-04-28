package com.findatex.validator.web.filter;

import com.findatex.validator.web.config.WebConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-IP rate limit on {@code POST /api/validate}. Token-bucket via Bucket4j:
 * capacity = {@link WebConfig.RateLimit#perIpPerHour()}, refill 1 token every
 * (3600 / capacity) seconds. Requests for /api/templates and /api/report/* are
 * not rate-limited (cheap reads / one-shot streams).
 */
@Provider
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    @Inject
    WebConfig config;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean unknownWarned =
            new java.util.concurrent.atomic.AtomicBoolean(false);
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

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (path == null) return;
        // Only throttle the heavy work (file upload + validation).
        if (!path.startsWith("api/validate") && !path.startsWith("/api/validate")) return;
        if (!"POST".equalsIgnoreCase(ctx.getMethod())) return;

        String key = clientIp(ctx);
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            ctx.abortWith(
                    Response.status(429)
                            .entity("Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.")
                            .header("Retry-After", retryAfterSeconds)
                            .build());
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(
                capacityPerHour,
                Refill.intervally(1, refillInterval));
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp(ContainerRequestContext ctx) {
        String fwd = ctx.getHeaderString("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            // First hop is the real client; subsequent ones are reverse proxies.
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        String real = ctx.getHeaderString("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        // No forwarded headers — log once so operators notice they're not behind a proxy
        // (or that proxy-address-forwarding isn't configured). Falling into the "unknown"
        // bucket means *all* anonymous traffic shares one rate limit, which is intentional
        // for local-dev but a misconfiguration for production.
        if (unknownWarned.compareAndSet(false, true)) {
            log.warn("No X-Forwarded-For/X-Real-IP on incoming request — all such traffic "
                    + "shares one rate-limit bucket. If you're in production, ensure your "
                    + "reverse proxy sets these headers.");
        }
        return "unknown";
    }
}
