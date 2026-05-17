package com.findatex.validator.newsletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findatex.validator.external.http.HttpExecutor;
import com.findatex.validator.external.http.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;

/**
 * UI-agnostic client the JavaFX desktop uses to subscribe to the newsletter
 * <b>via the web app</b> — the desktop never holds the provider API key (same
 * trust model as usage-stats). Reuses {@link HttpExecutor} so the call honours
 * the user's configured system/NTLM proxy.
 *
 * <p>Fully fault-tolerant: validation failures map to
 * {@link NewsletterStatus#INVALID_EMAIL}; any network/parse failure maps to
 * {@link NewsletterStatus#UNAVAILABLE}. Never throws. The address is sent only
 * to the configured endpoint and never logged here.
 *
 * <p>Shares {@link NewsletterStatus} / {@link EmailAddress} with the web layer;
 * the React frontend mirrors the same lowercase wire vocabulary.
 */
public final class NewsletterClient {

    private static final Logger log = LoggerFactory.getLogger(NewsletterClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SUBSCRIBE_PATH = "/api/newsletter/subscribe";

    private final HttpExecutor http;

    public NewsletterClient() {
        // Generous limiter: this is a rare, user-initiated single call.
        this(new HttpExecutor(new RateLimiter(5, 5)));
    }

    NewsletterClient(HttpExecutor http) {
        this.http = http;
    }

    /**
     * Subscribes {@code email} through the web app at {@code endpointBaseUrl}
     * (e.g. {@code https://validator.example.org}). Returns the resulting
     * status; never throws.
     */
    public NewsletterStatus subscribe(String endpointBaseUrl, String email) {
        if (!EmailAddress.isValid(email)) {
            return NewsletterStatus.INVALID_EMAIL;
        }
        if (endpointBaseUrl == null || endpointBaseUrl.isBlank()) {
            return NewsletterStatus.UNAVAILABLE;
        }
        try {
            URI uri = URI.create(trimTrailingSlash(endpointBaseUrl.trim()) + SUBSCRIBE_PATH);
            String body = MAPPER.createObjectNode()
                    .put("email", EmailAddress.normalise(email))
                    .toString();
            Optional<HttpExecutor.Response> resp = http.send(HttpExecutor.Request.post(
                    uri,
                    HttpExecutor.headers("Content-Type", "application/json",
                            "Accept", "application/json"),
                    body));
            if (resp.isEmpty()) {
                return NewsletterStatus.UNAVAILABLE;
            }
            return parse(resp.get());
        } catch (Exception e) {
            log.debug("Newsletter subscribe failed ({})", e.getClass().getSimpleName());
            return NewsletterStatus.UNAVAILABLE;
        }
    }

    private NewsletterStatus parse(HttpExecutor.Response r) {
        try {
            JsonNode status = MAPPER.readTree(r.body()).path("status");
            if (!status.isMissingNode() && !status.isNull()) {
                return NewsletterStatus.fromWire(status.asText());
            }
        } catch (Exception ignored) {
            // fall through to status-code heuristic
        }
        if (r.statusCode() == 400) return NewsletterStatus.INVALID_EMAIL;
        return NewsletterStatus.UNAVAILABLE;
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
