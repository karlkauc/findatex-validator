package com.findatex.validator.web.dto;

/**
 * Response body of {@code POST /api/quick-feedback}. {@code status} is a
 * lowercase {@link com.findatex.validator.quickfeedback.QuickFeedbackStatus}
 * wire token ({@code "ok"}, {@code "invalid"}, {@code "rate_limited"},
 * {@code "unavailable"}).
 */
public record QuickFeedbackResultDto(String status) {
}
