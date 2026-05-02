package com.findatex.validator.web.dto;

/**
 * Snapshot of the per-IP rate-limit bucket plus the operator-configured offline
 * download URL, surfaced via {@code GET /api/limits/status}.
 *
 * @param limit               configured tokens per window (e.g. 10)
 * @param remaining           tokens currently available for this IP
 * @param windowSeconds       refill window in seconds (3600)
 * @param resetInSeconds      seconds until the bucket is fully refilled (0 when full)
 * @param desktopDownloadUrl  optional URL of the JavaFX desktop build; null/blank means
 *                            the frontend renders the offline hint without a link
 */
public record RateLimitStatusDto(int limit, long remaining,
                                 int windowSeconds, long resetInSeconds,
                                 String desktopDownloadUrl) {
}
