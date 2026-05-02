package com.findatex.validator.web.api;

import com.findatex.validator.web.TestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestProfile(LimitsResourceTest.LimitsTestProfile.class)
class LimitsResourceTest {

    /**
     * Tight per-IP bucket of 2 so we can hit "remaining == 0" with two real uploads
     * and observe a non-zero {@code resetInSeconds}. Desktop download URL is set so
     * the resource passes it through untouched.
     */
    public static class LimitsTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "findatex.web.rate-limit.per-ip-per-hour", "2",
                    "findatex.web.desktop-download-url", "https://example.test/findatex-desktop");
        }
    }

    @Test
    void initialStatusExposesConfiguredLimitAndDesktopUrl() {
        given()
                .header("X-Forwarded-For", "10.0.0.1")
                .when().get("/api/limits/status")
                .then()
                .statusCode(200)
                .body("limit", equalTo(2))
                .body("remaining", equalTo(2))
                .body("windowSeconds", equalTo(3600))
                .body("resetInSeconds", is(0))
                .body("desktopDownloadUrl", equalTo("https://example.test/findatex-desktop"));
    }

    @Test
    void remainingDecrementsAfterEachValidate() {
        // Use a unique IP so this test doesn't share a bucket with the other test in this class.
        String ip = "10.0.0.2";

        given()
                .header("X-Forwarded-For", ip)
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then().statusCode(200);

        given()
                .header("X-Forwarded-For", ip)
                .when().get("/api/limits/status")
                .then()
                .statusCode(200)
                .body("limit", equalTo(2))
                .body("remaining", equalTo(1))
                .body("resetInSeconds", greaterThan(0));

        given()
                .header("X-Forwarded-For", ip)
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then().statusCode(200);

        given()
                .header("X-Forwarded-For", ip)
                .when().get("/api/limits/status")
                .then()
                .statusCode(200)
                .body("limit", equalTo(2))
                .body("remaining", equalTo(0))
                .body("resetInSeconds", greaterThan(0));
    }

    /** Status itself is never rate-limited — repeated reads stay 200. */
    @Test
    void statusEndpointIsNotRateLimited() {
        for (int i = 0; i < 10; i++) {
            given()
                    .header("X-Forwarded-For", "10.0.0.3")
                    .when().get("/api/limits/status")
                    .then().statusCode(200);
        }
    }
}
