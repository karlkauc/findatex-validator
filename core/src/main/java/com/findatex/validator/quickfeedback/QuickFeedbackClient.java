package com.findatex.validator.quickfeedback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.findatex.validator.AppInfo;
import com.findatex.validator.external.http.HttpExecutor;
import com.findatex.validator.external.http.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;

/**
 * UI-agnostic client the JavaFX desktop uses to submit a star rating
 * <b>via the web app</b> (same trust model as newsletter/usage-stats — the
 * desktop talks only to the configured endpoint). Reuses {@link HttpExecutor}
 * so the call honours the user's configured system/NTLM proxy.
 *
 * <p>Fully fault-tolerant: validation failures map to
 * {@link QuickFeedbackStatus#INVALID}; any network/parse failure maps to
 * {@link QuickFeedbackStatus#UNAVAILABLE}. Never throws. The comment text is
 * sent only to the configured endpoint and never logged here.
 *
 * <p>Shares {@link QuickFeedbackStatus} / {@link QuickFeedbackEntry} with the
 * web layer; the React frontend mirrors the same lowercase wire vocabulary.
 */
public final class QuickFeedbackClient {

    private static final Logger log = LoggerFactory.getLogger(QuickFeedbackClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SUBMIT_PATH = "/api/quick-feedback";

    private final HttpExecutor http;

    public QuickFeedbackClient() {
        // Generous limiter: this is a rare, user-initiated single call.
        this(new HttpExecutor(new RateLimiter(5, 5)));
    }

    QuickFeedbackClient(HttpExecutor http) {
        this.http = http;
    }

    /**
     * Submits a rating (1..5) with an optional comment and optional template
     * context through the web app at {@code endpointBaseUrl}
     * (e.g. {@code https://www.findatex-validator.eu}). Returns the resulting
     * status; never throws.
     */
    public QuickFeedbackStatus submit(String endpointBaseUrl, int rating, String comment, String templateId) {
        if (!QuickFeedbackEntry.isValidRating(rating) || !QuickFeedbackEntry.isValidComment(comment)) {
            return QuickFeedbackStatus.INVALID;
        }
        if (endpointBaseUrl == null || endpointBaseUrl.isBlank()) {
            return QuickFeedbackStatus.UNAVAILABLE;
        }
        try {
            URI uri = URI.create(trimTrailingSlash(endpointBaseUrl.trim()) + SUBMIT_PATH);
            ObjectNode node = MAPPER.createObjectNode()
                    .put("rating", rating)
                    .put("source", "desktop")
                    .put("appVersion", AppInfo.version());
            String normalisedComment = QuickFeedbackEntry.normaliseComment(comment);
            if (normalisedComment != null) {
                node.put("comment", normalisedComment);
            }
            if (templateId != null && !templateId.isBlank()) {
                node.put("templateId", templateId.trim());
            }
            Optional<HttpExecutor.Response> resp = http.send(HttpExecutor.Request.post(
                    uri,
                    HttpExecutor.headers("Content-Type", "application/json",
                            "Accept", "application/json"),
                    node.toString()));
            if (resp.isEmpty()) {
                return QuickFeedbackStatus.UNAVAILABLE;
            }
            return parse(resp.get());
        } catch (Exception e) {
            log.debug("Quick-feedback submit failed ({})", e.getClass().getSimpleName());
            return QuickFeedbackStatus.UNAVAILABLE;
        }
    }

    private QuickFeedbackStatus parse(HttpExecutor.Response r) {
        try {
            JsonNode status = MAPPER.readTree(r.body()).path("status");
            if (!status.isMissingNode() && !status.isNull()) {
                return QuickFeedbackStatus.fromWire(status.asText());
            }
        } catch (Exception ignored) {
            // fall through to status-code heuristic
        }
        if (r.statusCode() == 400) return QuickFeedbackStatus.INVALID;
        // Defensive: HttpExecutor currently retries 429s and gives up with an
        // empty Optional (→ UNAVAILABLE above), so this branch only fires if
        // that behaviour ever changes.
        if (r.statusCode() == 429) return QuickFeedbackStatus.RATE_LIMITED;
        return QuickFeedbackStatus.UNAVAILABLE;
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
