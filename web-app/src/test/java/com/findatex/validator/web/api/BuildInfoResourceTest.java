package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class BuildInfoResourceTest {

    @Test
    void exposesVersionAndGitFields() {
        given()
                .when().get("/api/build-info")
                .then()
                .statusCode(200)
                // Version comes from Maven (quarkus.application.version). For this repo
                // it's currently "1.0.0"; allow any semver-ish string so a future bump
                // doesn't break the test.
                .body("version", matchesPattern("^\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?$"))
                // commit is empty when the plugin couldn't read .git (e.g. shallow CI
                // checkouts), otherwise a 7-char abbrev hex string.
                .body("commit", anyOf(equalTo(""), matchesPattern("^[0-9a-f]{7}$")))
                .body("dirty", is(notNullValue()))   // boolean — any value is fine
                .body("buildTime", notNullValue());  // string, possibly empty
    }
}
