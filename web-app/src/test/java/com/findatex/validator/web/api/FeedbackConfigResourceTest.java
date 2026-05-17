package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class FeedbackConfigResourceTest {

    @Test
    void returnsNullWhenNoRepoConfigured() {
        given()
                .when().get("/api/feedback-config")
                .then()
                .statusCode(200)
                .body("githubRepo", nullValue());
    }
}
