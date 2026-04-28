package com.findatex.validator.web.api;

import com.findatex.validator.web.TestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(RateLimitFilterTest.LowLimitProfile.class)
class RateLimitFilterTest {

    public static class LowLimitProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("findatex.web.rate-limit.per-ip-per-hour", "2");
        }
    }

    @Test
    void thirdRequestExceedsTheBucketAndReturns429() {
        // Two valid uploads consume the full bucket.
        for (int i = 0; i < 2; i++) {
            given()
                    .multiPart("templateId", "TPT")
                    .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                    .when().post("/api/validate")
                    .then().statusCode(200);
        }
        given()
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then()
                .statusCode(429)
                .header("Retry-After", org.hamcrest.Matchers.notNullValue());
    }
}
