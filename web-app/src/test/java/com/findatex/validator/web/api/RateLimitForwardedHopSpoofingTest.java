package com.findatex.validator.web.api;

import com.findatex.validator.web.TestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Cloud Run shape ({@code forwarded-hops=1}): the platform appends the real
 * client address as the right-most {@code X-Forwarded-For} entry. Whatever the
 * client puts in front of it must not change the bucket — Quarkus' own proxy
 * forwarding took the left-most entry, which is exactly that client-controlled
 * part.
 */
@QuarkusTest
@TestProfile(RateLimitForwardedHopSpoofingTest.OneHopProfile.class)
class RateLimitForwardedHopSpoofingTest {

    public static class OneHopProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "findatex.web.rate-limit.per-ip-per-hour", "2",
                    "findatex.web.client-ip.forwarded-hops", "1");
        }
    }

    private static final String REAL = "203.0.113.77";

    @Test
    void prependedValuesCannotMintFreshBuckets() {
        validate("1.1.1.1, " + REAL).then().statusCode(200);
        validate("2.2.2.2, 3.3.3.3, " + REAL).then().statusCode(200);
        validate("evil.example.com, " + REAL).then().statusCode(429);
    }

    @Test
    void nonLiteralRightMostEntryFallsBackToTheTcpPeer() {
        // A hostname in the trusted position must neither be resolved nor become
        // its own bucket key: both requests share the 127.0.0.1 bucket.
        validate("attacker-a.example").then().statusCode(200);
        validate("attacker-b.example").then().statusCode(200);
        validate("attacker-c.example").then().statusCode(429);
    }

    private static io.restassured.response.Response validate(String xff) {
        return given()
                .header("X-Forwarded-For", xff)
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate");
    }
}
