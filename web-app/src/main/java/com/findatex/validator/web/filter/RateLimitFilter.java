package com.findatex.validator.web.filter;

import com.findatex.validator.web.service.RateLimitService;
import io.github.bucket4j.ConsumptionProbe;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Per-IP rate limit on {@code POST /api/validate}. Delegates the bucket map to
 * {@link RateLimitService}; the bucket key is the TCP source IP that Vert.x
 * exposes on {@link HttpServerRequest#remoteAddress()} (overridden by Quarkus
 * itself only when {@code quarkus.http.proxy.proxy-address-forwarding=true} is
 * combined with a {@code trusted-proxies} CIDR list — see application.properties).
 */
@Provider
public class RateLimitFilter implements ContainerRequestFilter {

    @Inject
    RateLimitService rateLimits;

    @Context
    HttpServerRequest request;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (path == null) return;
        // Only throttle the heavy work (file upload + validation).
        if (!path.startsWith("api/validate") && !path.startsWith("/api/validate")) return;
        if (!"POST".equalsIgnoreCase(ctx.getMethod())) return;

        ConsumptionProbe probe = rateLimits.consume(clientIp());
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            ctx.abortWith(
                    Response.status(429)
                            .entity("Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.")
                            .header("Retry-After", retryAfterSeconds)
                            .build());
        }
    }

    private String clientIp() {
        if (request == null || request.remoteAddress() == null) return null;
        return request.remoteAddress().host();
    }
}
