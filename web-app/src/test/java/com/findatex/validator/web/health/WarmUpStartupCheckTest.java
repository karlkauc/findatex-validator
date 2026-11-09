package com.findatex.validator.web.health;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;

/**
 * The Cloud Run startup probe target exists and carries the warm-up check
 * (warm-up is disabled in %test, so it reports UP).
 */
@QuarkusTest
class WarmUpStartupCheckTest {

    @Test
    void startedEndpointReportsWarmUpCheck() {
        given().when().get("/_internal/health/started")
                .then()
                .statusCode(200)
                .body("checks.name", hasItem("warm-up"));
    }
}
