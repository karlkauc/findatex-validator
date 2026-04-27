package com.tpt.validator.external.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Optional;

public final class HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpExecutor.class);
    private static final int MAX_ATTEMPTS = 3;

    private final RateLimiter limiter;
    private final long backoffMs;

    public HttpExecutor(RateLimiter limiter) { this(limiter, 2_000L); }

    HttpExecutor(RateLimiter limiter, long backoffMs) {
        this.limiter = limiter;
        this.backoffMs = backoffMs;
    }

    public Optional<HttpResponse<String>> send(HttpRequest req) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            limiter.acquire();
            try {
                HttpResponse<String> r = HttpClientFactory.get().send(req, BodyHandlers.ofString());
                if (r.statusCode() == 429 || r.statusCode() / 100 == 5) {
                    log.debug("HTTP {} from {} (attempt {}/{}), backing off", r.statusCode(),
                            req.uri(), attempt, MAX_ATTEMPTS);
                    if (attempt < MAX_ATTEMPTS) sleep(backoffMs * (1L << (attempt - 1)));
                    continue;
                }
                return Optional.of(r);
            } catch (Exception e) {
                log.debug("HTTP error to {} (attempt {}/{}): {}", req.uri(), attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) sleep(backoffMs * (1L << (attempt - 1)));
            }
        }
        log.warn("Giving up on {} after {} attempts", req.uri(), MAX_ATTEMPTS);
        return Optional.empty();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
