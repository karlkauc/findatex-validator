package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Provider key configured ⇒ feature enabled. We only exercise the paths that
 * do <b>not</b> make an outbound provider call: an invalid e-mail is rejected
 * (400) before the provider is contacted, and the dedicated per-IP bucket
 * yields 429 once exhausted. A valid address would hit the real MailerLite API
 * and is intentionally not tested here.
 */
@QuarkusTest
@TestProfile(NewsletterResourceTest.WithKey.class)
class NewsletterResourceTest {

    @Test
    void configReportsEnabled() {
        given().when().get("/api/newsletter-config")
                .then().statusCode(200).body("enabled", is(true));
    }

    @Test
    void invalidEmailIs400BeforeAnyProviderCall() {
        given().header("X-Forwarded-For", "203.0.113.21")
                .contentType("application/json")
                .body("{\"email\":\"not-an-email\"}")
                .when().post("/api/newsletter/subscribe")
                .then().statusCode(400).body("status", equalTo("invalid_email"));
    }

    @Test
    void emptyBodyIs400() {
        given().header("X-Forwarded-For", "203.0.113.22")
                .contentType("application/json")
                .body("{}")
                .when().post("/api/newsletter/subscribe")
                .then().statusCode(400).body("status", equalTo("invalid_email"));
    }

    @Test
    void rateLimitedAfterBudget() {
        String ip = "203.0.113.23";
        // Rate filter consumes a token per POST regardless of outcome; invalid
        // e-mails keep us off the network while still draining the bucket.
        for (int i = 0; i < 2; i++) {
            given().header("X-Forwarded-For", ip)
                    .contentType("application/json")
                    .body("{\"email\":\"bad\"}")
                    .when().post("/api/newsletter/subscribe")
                    .then().statusCode(400);
        }
        given().header("X-Forwarded-For", ip)
                .contentType("application/json")
                .body("{\"email\":\"bad\"}")
                .when().post("/api/newsletter/subscribe")
                .then().statusCode(429);
    }

    public static final class WithKey implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "findatex.web.newsletter.api-key", "test-key",
                    "findatex.web.newsletter.rate-per-ip-per-hour", "2");
        }
    }
}
