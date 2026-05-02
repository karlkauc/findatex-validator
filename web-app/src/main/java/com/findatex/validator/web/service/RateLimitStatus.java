package com.findatex.validator.web.service;

/**
 * Snapshot of the per-IP rate-limit bucket as exposed by
 * {@link RateLimitService#inspect(String, String)}.
 *
 * @param limit          configured tokens per window (e.g. 10)
 * @param remaining      tokens currently available for this IP
 * @param windowSeconds  refill window in seconds (3600 today)
 * @param resetInSeconds seconds until the bucket is fully refilled (0 when full)
 */
public record RateLimitStatus(int limit, long remaining,
                              int windowSeconds, long resetInSeconds) {
}
