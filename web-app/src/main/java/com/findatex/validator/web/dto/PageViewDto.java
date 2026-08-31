package com.findatex.validator.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body of {@code POST /api/page-view}. Three fields, all optional and
 * all sanitised server-side.
 *
 * <p>{@code referrer} is the raw {@code document.referrer} — the server keeps
 * only its <b>host</b>, never the full URL, because the path of the page
 * someone came from can itself be personal data (a search query, an internal
 * ticket URL). {@code campaign} carries {@code ?utm_source=} / {@code ?ref=} so
 * a LinkedIn post or a conference handout can be told apart from organic
 * traffic. {@code path} is the SPA route, so per-page traffic stays visible
 * once there is more than one page.
 *
 * <p>No id, no fingerprint, no IP: this counts page loads, not people.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageViewDto(String path, String referrer, String campaign) {
}
