package com.findatex.validator.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body of {@code POST /api/quick-feedback}. Deliberately minimal:
 * {@code rating} (1..5) is the only required field; {@code comment} is free
 * text (max 2000 chars), {@code source} is {@code desktop}/{@code web}
 * (anything else is clamped to {@code web} server-side), {@code appVersion}
 * and {@code templateId} are optional context. No install id, ever — feedback
 * must not be linkable to usage events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuickFeedbackSubmitDto(Integer rating, String comment, String source,
                                     String appVersion, String templateId) {
}
