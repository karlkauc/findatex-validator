package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Default config: no datasource URL. The feature is inert — config reports
 * {@code enabled:false} and submit returns 503 {@code unavailable} so the SPA
 * hides the widget and the app boots without a DB.
 */
@QuarkusTest
class QuickFeedbackResourceDisabledTest {

    @Test
    void configReportsDisabled() {
        given().when().get("/api/quick-feedback-config")
                .then().statusCode(200).body("enabled", is(false));
    }

    @Test
    void submitIs503Unavailable() {
        given().header("X-Forwarded-For", "198.51.100.31")
                .contentType("application/json")
                .body("{\"rating\":5}")
                .when().post("/api/quick-feedback")
                .then().statusCode(503).body("status", equalTo("unavailable"));
    }
}
