package com.findatex.validator.web.dto;

/**
 * Tells the SPA whether the quick-feedback (star rating) widget should be
 * shown. {@code enabled} is {@code true} only when the usage-stats datasource
 * is configured (mirrors the {@code /api/newsletter-config} pattern).
 */
public record QuickFeedbackConfigDto(boolean enabled) {
}
