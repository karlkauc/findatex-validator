package com.findatex.validator.web.dto;

/**
 * Response body of {@code POST /api/newsletter/subscribe}. {@code status} is a
 * lowercase {@link com.findatex.validator.newsletter.NewsletterStatus} wire
 * token (e.g. {@code "pending"}, {@code "already_subscribed"},
 * {@code "invalid_email"}, {@code "unavailable"}).
 */
public record NewsletterResultDto(String status) {
}
