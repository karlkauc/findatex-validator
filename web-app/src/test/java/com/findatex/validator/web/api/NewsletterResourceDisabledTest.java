package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Default config: no provider API key. The feature is inert — config reports
 * {@code enabled:false} and subscribe returns 503 {@code unavailable} so the
 * SPA hides the form and the app boots without any provider.
 */
@QuarkusTest
class NewsletterResourceDisabledTest {

    @Test
    void configReportsDisabled() {
        given().when().get("/api/newsletter-config")
                .then().statusCode(200).body("enabled", is(false));
    }

    @Test
    void subscribeIs503Unavailable() {
        given().header("X-Forwarded-For", "198.51.100.21")
                .contentType("application/json")
                .body("{\"email\":\"a@b.co\"}")
                .when().post("/api/newsletter/subscribe")
                .then().statusCode(503).body("status", equalTo("unavailable"));
    }
}
