package com.findatex.validator.web.api;

import com.findatex.validator.web.TestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Verifies the rate-limit bucket key is the rightmost entry in {@code X-Forwarded-For}
 * (the IP appended by the trusted reverse proxy), not a value the client can spoof.
 */
@QuarkusTest
@TestProfile(RateLimitFilterSpoofingTest.LowLimitSpoofProfile.class)
class RateLimitFilterSpoofingTest {

    public static class LowLimitSpoofProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("findatex.web.rate-limit.per-ip-per-hour", "2");
        }
    }

    @Test
    void leftmostXForwardedForCannotMintFreshBuckets() {
        // Two requests with the same rightmost (= proxy-appended) IP exhaust that bucket,
        // even though the leftmost entry — which a client could forge — differs each time.
        given()
                .header("X-Forwarded-For", "1.1.1.1, 10.20.30.40")
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then().statusCode(200);

        given()
                .header("X-Forwarded-For", "2.2.2.2, 10.20.30.40")
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then().statusCode(200);

        // A third request from the same proxy-appended IP must be throttled — even when
        // the leftmost entry is a brand-new spoofed address.
        given()
                .header("X-Forwarded-For", "9.9.9.9, 10.20.30.40")
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then()
                .statusCode(429)
                .header("Retry-After", org.hamcrest.Matchers.notNullValue());
    }
}
