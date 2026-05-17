package com.findatex.validator.web.dto;

/**
 * Tells the SPA whether the newsletter sign-up form should be shown.
 * {@code enabled} is {@code true} only when the operator configured a provider
 * API key (mirrors the {@code /api/feedback-config} pattern).
 */
public record NewsletterConfigDto(boolean enabled) {
}
