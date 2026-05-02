package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * When {@code findatex.web.desktop-download-url} is unset, the resource must
 * return {@code desktopDownloadUrl: null} so the frontend renders the offline
 * hint without a clickable link.
 */
@QuarkusTest
@TestProfile(LimitsResourceWithoutDesktopUrlTest.NoDesktopUrlProfile.class)
class LimitsResourceWithoutDesktopUrlTest {

    public static class NoDesktopUrlProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Generous bucket — this test only inspects status, never consumes.
            // Empty desktop-download-url overrides the production default so we can
            // assert the "URL not configured" branch (resource returns null).
            return Map.of(
                    "findatex.web.rate-limit.per-ip-per-hour", "100",
                    "findatex.web.desktop-download-url", "");
        }
    }

    @Test
    void desktopDownloadUrlIsNullWhenUnset() {
        given()
                .header("X-Forwarded-For", "10.0.0.4")
                .when().get("/api/limits/status")
                .then()
                .statusCode(200)
                .body("desktopDownloadUrl", nullValue())
                .body("limit", greaterThanOrEqualTo(1));
    }
}
