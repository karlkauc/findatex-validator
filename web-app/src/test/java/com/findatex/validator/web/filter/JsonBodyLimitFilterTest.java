package com.findatex.validator.web.filter;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JSON endpoints take a few hundred bytes; the global 25 MB body limit is
 * for the upload only.
 */
@QuarkusTest
class JsonBodyLimitFilterTest {

    @Test
    void oversizedJsonBodiesAreRejectedBeforeParsing() {
        String big = "{\"path\":\"/" + "a".repeat((int) JsonBodyLimitFilter.MAX_BYTES) + "\"}";
        for (String path : new String[] {"/api/page-view", "/api/quick-feedback", "/api/usage-stats",
                "/api/newsletter/subscribe"}) {
            given().header("X-Forwarded-For", "203.0.113.200")
                    .contentType("application/json").body(big)
                    .when().post(path)
                    .then().statusCode(413);
        }
    }

    @Test
    void normalSizedBodiesPass() {
        given().header("X-Forwarded-For", "203.0.113.201")
                .contentType("application/json").body("{\"path\":\"/\"}")
                .when().post("/api/page-view")
                .then().statusCode(204);
    }

    @Test
    void onlyTheJsonEndpointsAreCovered() {
        assertThat(JsonBodyLimitFilter.isJsonEndpoint("/api/validate")).isFalse();
        assertThat(JsonBodyLimitFilter.isJsonEndpoint("/api/page-view")).isTrue();
    }
}
