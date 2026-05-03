package com.findatex.validator.web.api;

import com.findatex.validator.web.TestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Default-secure regression: when the Quarkus proxy-forwarding switches are OFF
 * (production default), no value of {@code X-Forwarded-For} can mint a fresh
 * rate-limit bucket. All requests from the same TCP source IP share one bucket
 * even when the attacker rotates the header on every request.
 *
 * <p>This profile overrides the global %test setting that enables trusted-proxy
 * forwarding, so the bucket key falls back to the TCP source address (127.0.0.1
 * for all RestAssured calls).
 */
@QuarkusTest
@TestProfile(RateLimitFilterSpoofingTest.NoTrustedProxyProfile.class)
class RateLimitFilterSpoofingTest {

    public static class NoTrustedProxyProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "findatex.web.rate-limit.per-ip-per-hour", "2",
                    // Explicitly turn off proxy-address-forwarding to simulate the
                    // production default where the service is exposed without a
                    // sanitising LB in front.
                    "quarkus.http.proxy.proxy-address-forwarding", "false",
                    "quarkus.http.proxy.allow-x-forwarded", "false",
                    "quarkus.http.proxy.trusted-proxies", "");
        }
    }

    @Test
    void rotatingXForwardedForCannotMintFreshBuckets() {
        // Two valid uploads with attacker-rotated X-Forwarded-For values consume
        // the bucket keyed on the TCP source IP (127.0.0.1) — the headers are
        // ignored because proxy-address-forwarding is OFF.
        given()
                .header("X-Forwarded-For", "1.1.1.1")
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then().statusCode(200);

        given()
                .header("X-Forwarded-For", "2.2.2.2, 3.3.3.3, 4.4.4.4")
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then().statusCode(200);

        // A third request, again with a brand-new spoofed header, must be 429.
        given()
                .header("X-Forwarded-For", "9.9.9.9")
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then()
                .statusCode(429)
                .header("Retry-After", org.hamcrest.Matchers.notNullValue());
    }
}
