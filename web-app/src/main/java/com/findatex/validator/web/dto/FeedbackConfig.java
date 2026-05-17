package com.findatex.validator.web.dto;

/**
 * Tells the SPA whether (and where) the "report a false positive" action
 * should be offered. {@code githubRepo} is an {@code owner/repo} slug, or
 * {@code null} when the operator has not configured feedback.
 */
public record FeedbackConfig(String githubRepo) {
}
