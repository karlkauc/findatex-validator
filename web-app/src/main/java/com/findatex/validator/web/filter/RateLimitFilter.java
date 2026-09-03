package com.findatex.validator.web.filter;

import com.findatex.validator.stats.UsageEvent;
import com.findatex.validator.web.service.ClientContextFactory;
import com.findatex.validator.web.service.RateLimitService;
import com.findatex.validator.web.service.UsageStatsService;
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

    @Inject
    UsageStatsService usageStats;

    @Inject
    ClientContextFactory clientContexts;

    @Context
    HttpServerRequest request;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (path == null) return;
        if (!"POST".equalsIgnoreCase(ctx.getMethod())) return;

        boolean isValidate = path.startsWith("api/validate") || path.startsWith("/api/validate");
        boolean isUsage = path.startsWith("api/usage-stats") || path.startsWith("/api/usage-stats");
        boolean isNewsletter = path.startsWith("api/newsletter") || path.startsWith("/api/newsletter");
        // Also matches /api/quick-feedback-config, but the POST-only guard above
        // keeps that GET outside the limiter.
        boolean isQuickFeedback = path.startsWith("api/quick-feedback") || path.startsWith("/api/quick-feedback");
        boolean isPageView = path.startsWith("api/page-view") || path.startsWith("/api/page-view");
        if (!isValidate && !isUsage && !isNewsletter && !isQuickFeedback && !isPageView) return;

        ConsumptionProbe probe = isPageView
                ? rateLimits.consumePageView(clientIp())
                : isQuickFeedback
                        ? rateLimits.consumeQuickFeedback(clientIp())
                        : isNewsletter
                                ? rateLimits.consumeNewsletter(clientIp())
                                : isUsage
                                        ? rateLimits.consumeUsage(clientIp())
                                        : rateLimits.consume(clientIp());
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            if (isValidate) recordRateLimited();
            ctx.abortWith(
                    Response.status(429)
                            .entity("Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.")
                            .header("Retry-After", retryAfterSeconds)
                            .build());
        }
    }

    private String clientIp() {
        return ClientContextFactory.clientIp(request);
    }

    /**
     * A throttled validation is still a (failed) validation attempt for the
     * statistics — without it a user hitting the limit looks like a user who
     * left. Best-effort, never affects the 429.
     */
    private void recordRateLimited() {
        try {
            usageStats.recordFailedRun(UsageEvent.STATUS_RATE_LIMITED, null, null,
                    null, null, null, clientContexts.from(request));
        } catch (RuntimeException e) {
            // stats must never affect the request path
        }
    }
}
