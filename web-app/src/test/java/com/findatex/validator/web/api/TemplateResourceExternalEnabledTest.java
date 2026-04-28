package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * With the operator master switch on, every template/version that declares any ISIN/LEI
 * columns must surface {@code externalAvailable=true}. The default-profile test
 * ({@link TemplateResourceTest}) covers the operator-off case.
 */
@QuarkusTest
@TestProfile(TemplateResourceExternalEnabledTest.OperatorOnProfile.class)
class TemplateResourceExternalEnabledTest {

    @Test
    void allFourTemplatesAdvertiseExternalValidation() {
        given()
                .when().get("/api/templates")
                .then()
                .statusCode(200)
                .body("findAll { it.externalAvailable == true }.size()", is(4));
    }

    public static final class OperatorOnProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("findatex.web.external.enabled", "true");
        }
    }
}
