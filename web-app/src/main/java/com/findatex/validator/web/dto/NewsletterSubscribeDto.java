package com.findatex.validator.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body of {@code POST /api/newsletter/subscribe}. The e-mail is the
 * only input; it is never persisted or logged by this service — it is handed
 * straight to the external newsletter provider.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsletterSubscribeDto(String email) {
}
