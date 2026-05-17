package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Token configured (no DB ⇒ inert insert path). Verifies the ingest contract:
 * 401 on wrong/missing token, 202 on valid token, 202 on garbage JSON, and
 * 429 once the dedicated usage-stats per-IP bucket is exhausted.
 */
@QuarkusTest
@TestProfile(UsageStatsResourceTest.WithToken.class)
class UsageStatsResourceTest {

    private static final String VALID_JSON = """
            {"installId":"a1b2c3d4-0000-0000-0000-000000000001","source":"desktop",
             "appVersion":"dev","osName":"Linux","templateId":"TPT","templateVersion":"V7",
             "profiles":["EIOPA_QRT"],"mode":"single","fileCount":1,"rowCount":10,
             "errorCount":0,"warningCount":0,"infoCount":0,"overallScore":99.5,
             "durationMs":42,"externalEnabled":false,"ruleIds":["XF-16"],
             "clientEventAt":"2026-05-17T09:59:58Z"}""";

    @Test
    void missingTokenIs401() {
        given().header("X-Forwarded-For", "203.0.113.1")
                .contentType("application/json").body(VALID_JSON)
                .when().post("/api/usage-stats")
                .then().statusCode(401);
    }

    @Test
    void wrongTokenIs401() {
        given().header("X-Forwarded-For", "203.0.113.2")
                .header("X-Usage-Token", "nope")
                .contentType("application/json").body(VALID_JSON)
                .when().post("/api/usage-stats")
                .then().statusCode(401);
    }

    @Test
    void validTokenIs202() {
        given().header("X-Forwarded-For", "203.0.113.3")
                .header("X-Usage-Token", "test-secret")
                .contentType("application/json").body(VALID_JSON)
                .when().post("/api/usage-stats")
                .then().statusCode(202);
    }

    @Test
    void garbageJsonIs202() {
        given().header("X-Forwarded-For", "203.0.113.4")
                .header("X-Usage-Token", "test-secret")
                .contentType("application/json").body("{not json")
                .when().post("/api/usage-stats")
                .then().statusCode(202);
    }

    @Test
    void rateLimitedAfterBudget() {
        String ip = "203.0.113.5";
        for (int i = 0; i < 2; i++) {
            given().header("X-Forwarded-For", ip)
                    .header("X-Usage-Token", "test-secret")
                    .contentType("application/json").body(VALID_JSON)
                    .when().post("/api/usage-stats")
                    .then().statusCode(202);
        }
        given().header("X-Forwarded-For", ip)
                .header("X-Usage-Token", "test-secret")
                .contentType("application/json").body(VALID_JSON)
                .when().post("/api/usage-stats")
                .then().statusCode(429);
    }

    public static final class WithToken implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "findatex.web.usage-stats.ingest-token", "test-secret",
                    "findatex.web.usage-stats.rate-per-ip-per-hour", "2");
        }
    }
}
