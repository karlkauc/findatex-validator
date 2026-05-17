package com.findatex.validator.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findatex.validator.newsletter.NewsletterStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * MailerLite "connect" API binding: a single
 * {@code POST https://connect.mailerlite.com/api/subscribers}. Double-opt-in is
 * a MailerLite <i>account</i> setting — when it is on, a freshly created
 * subscriber comes back with status {@code unconfirmed} and MailerLite sends
 * the confirmation mail itself; this binding does not (and cannot) send mail.
 *
 * <p>Response mapping (HTTP status × body {@code data.status}):
 * <ul>
 *   <li>201 created → {@code unconfirmed} ⇒ PENDING, else ⇒ SUBSCRIBED</li>
 *   <li>200 existing → {@code unconfirmed} ⇒ ALREADY_PENDING,
 *       else ⇒ ALREADY_SUBSCRIBED</li>
 *   <li>422 ⇒ INVALID_EMAIL</li>
 *   <li>anything else / timeout / I/O ⇒ UNAVAILABLE (logged, no e-mail)</li>
 * </ul>
 */
final class MailerLiteProvider implements NewsletterProvider {

    private static final Logger log = LoggerFactory.getLogger(MailerLiteProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final URI ENDPOINT = URI.create("https://connect.mailerlite.com/api/subscribers");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String apiKey;
    private final String groupId;
    private final HttpClient http;

    MailerLiteProvider(String apiKey, String groupId) {
        this.apiKey = apiKey;
        this.groupId = groupId == null ? "" : groupId.trim();
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public NewsletterStatus subscribe(String email) {
        try {
            String body = requestBody(email);
            HttpRequest req = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return map(resp.statusCode(), resp.body());
        } catch (Exception e) {
            // Timeout / I/O / interruption — never leak the address.
            log.warn("Newsletter provider call failed ({})", e.getClass().getSimpleName());
            return NewsletterStatus.UNAVAILABLE;
        }
    }

    private String requestBody(String email) {
        // Build with Jackson so the address is correctly JSON-escaped.
        var root = MAPPER.createObjectNode();
        root.put("email", email);
        if (!groupId.isEmpty()) {
            var groups = root.putArray("groups");
            groups.add(groupId);
        }
        return root.toString();
    }

    private NewsletterStatus map(int status, String body) {
        if (status == 422) return NewsletterStatus.INVALID_EMAIL;
        if (status != 200 && status != 201) {
            // 401/403 (bad key) and 429/5xx all collapse to a soft failure.
            log.warn("Newsletter provider returned HTTP {}", status);
            return NewsletterStatus.UNAVAILABLE;
        }
        boolean unconfirmed = "unconfirmed".equals(subscriberStatus(body));
        if (status == 201) {
            return unconfirmed ? NewsletterStatus.PENDING : NewsletterStatus.SUBSCRIBED;
        }
        return unconfirmed ? NewsletterStatus.ALREADY_PENDING : NewsletterStatus.ALREADY_SUBSCRIBED;
    }

    /** Reads {@code data.status} defensively; {@code null} if absent/unparseable. */
    private String subscriberStatus(String body) {
        try {
            JsonNode data = MAPPER.readTree(body).path("data").path("status");
            return data.isMissingNode() || data.isNull() ? null : data.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
