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
 * Datasource URL configured ⇒ feature enabled. The URL is deliberately
 * unroutable ({@code 127.0.0.1:1}): the resource answers optimistically before
 * the async insert runs, so the request path never touches the DB — the
 * insert fails harmlessly on the daemon thread (covered separately by
 * {@link com.findatex.validator.web.service.QuickFeedbackServiceRetryTest}).
 */
@QuarkusTest
@TestProfile(QuickFeedbackResourceTest.WithDb.class)
class QuickFeedbackResourceTest {

    @Test
    void configReportsEnabled() {
        given().when().get("/api/quick-feedback-config")
                .then().statusCode(200).body("enabled", is(true));
    }

    @Test
    void missingRatingIs400Invalid() {
        given().header("X-Forwarded-For", "203.0.113.31")
                .contentType("application/json")
                .body("{}")
                .when().post("/api/quick-feedback")
                .then().statusCode(400).body("status", equalTo("invalid"));
    }

    @Test
    void outOfRangeRatingIs400Invalid() {
        given().header("X-Forwarded-For", "203.0.113.32")
                .contentType("application/json")
                .body("{\"rating\":0}")
                .when().post("/api/quick-feedback")
                .then().statusCode(400).body("status", equalTo("invalid"));
        given().header("X-Forwarded-For", "203.0.113.32")
                .contentType("application/json")
                .body("{\"rating\":6}")
                .when().post("/api/quick-feedback")
                .then().statusCode(400).body("status", equalTo("invalid"));
    }

    @Test
    void overlongCommentIs400Invalid() {
        String comment = "x".repeat(2001);
        given().header("X-Forwarded-For", "203.0.113.33")
                .contentType("application/json")
                .body("{\"rating\":3,\"comment\":\"" + comment + "\"}")
                .when().post("/api/quick-feedback")
                .then().statusCode(400).body("status", equalTo("invalid"));
    }

    @Test
    void validRatingIs200Ok() {
        given().header("X-Forwarded-For", "203.0.113.34")
                .contentType("application/json")
                .body("{\"rating\":5,\"comment\":\"great tool\",\"source\":\"web\",\"templateId\":\"TPT\"}")
                .when().post("/api/quick-feedback")
                .then().statusCode(200).body("status", equalTo("ok"));
    }

    @Test
    void rateLimitedAfterBudget() {
        String ip = "203.0.113.35";
        // Rate filter consumes a token per POST regardless of outcome; invalid
        // ratings keep the requests cheap while still draining the bucket.
        for (int i = 0; i < 2; i++) {
            given().header("X-Forwarded-For", ip)
                    .contentType("application/json")
                    .body("{\"rating\":0}")
                    .when().post("/api/quick-feedback")
                    .then().statusCode(400);
        }
        given().header("X-Forwarded-For", ip)
                .contentType("application/json")
                .body("{\"rating\":0}")
                .when().post("/api/quick-feedback")
                .then().statusCode(429);
    }

    public static final class WithDb implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    // Unroutable on purpose — makes enabled() true without a real DB.
                    "quarkus.datasource.jdbc.url", "jdbc:postgresql://127.0.0.1:1/none",
                    "quarkus.datasource.jdbc.acquisition-timeout", "1S",
                    "findatex.web.quick-feedback.rate-per-ip-per-hour", "2");
        }
    }
}
