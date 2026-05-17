package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(FeedbackConfigResourceConfiguredTest.WithRepo.class)
class FeedbackConfigResourceConfiguredTest {

    @Test
    void returnsConfiguredRepo() {
        given()
                .when().get("/api/feedback-config")
                .then()
                .statusCode(200)
                .body("githubRepo", equalTo("karlkauc/findatex-validator"));
    }

    public static final class WithRepo implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("findatex.web.feedback.github-repo", "karlkauc/findatex-validator");
        }
    }
}
