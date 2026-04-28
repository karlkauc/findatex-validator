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

    private static String clientIp(ContainerRequestContext ctx) {
        String fwd = ctx.getHeaderString("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            // First hop is the real client; subsequent ones are reverse proxies.
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        String real = ctx.getHeaderString("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        // RestEasy Reactive: the request's remote-address is exposed via SecurityContext or
        // through the request's underlying VertxHttpServerRequest. As a portable fallback,
        // we key by a constant 'unknown' bucket — operators behind a proxy should set
        // X-Forwarded-For. (Quarkus can be told to populate the request's remoteAddress
        // by setting quarkus.http.proxy.proxy-address-forwarding=true.)
        return "unknown";
    }
}
