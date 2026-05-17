package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Default config: no ingest token, no datasource. The endpoint must
 * accept-and-discard (202) so the app boots and serves without any DB.
 */
@QuarkusTest
class UsageStatsResourceDisabledTest {

    @Test
    void noTokenConfiguredAccepts202() {
        given().header("X-Forwarded-For", "198.51.100.7")
                .contentType("application/json")
                .body("{\"source\":\"desktop\"}")
                .when().post("/api/usage-stats")
                .then().statusCode(202);
    }
}
