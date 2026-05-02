package com.findatex.validator.web.filter;

import com.findatex.validator.web.service.RateLimitService;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Per-IP rate limit on {@code POST /api/validate}. Delegates the bucket map and
 * IP-extraction to {@link RateLimitService} so the read-only status endpoint
 * (GET /api/limits/status) can inspect the same buckets without consuming tokens.
 */
@Provider
public class RateLimitFilter implements ContainerRequestFilter {

    @Inject
    RateLimitService rateLimits;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (path == null) return;
        // Only throttle the heavy work (file upload + validation).
        if (!path.startsWith("api/validate") && !path.startsWith("/api/validate")) return;
        if (!"POST".equalsIgnoreCase(ctx.getMethod())) return;

        ConsumptionProbe probe = rateLimits.consume(
                ctx.getHeaderString("X-Forwarded-For"),
                ctx.getHeaderString("X-Real-IP"));
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            ctx.abortWith(
                    Response.status(429)
                            .entity("Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.")
                            .header("Retry-After", retryAfterSeconds)
                            .build());
        }
    }
}
