package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class AboutResourceTest {

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @Test
    void aboutShowsMavenVersionInsteadOfPlaceholder() {
        given()
                .when().get("/api/about")
                .then()
                .statusCode(200)
                .body(containsString("**Version " + version + "**"))
                .body(not(containsString("{{version}}")))
                .body(not(containsString("Version 1.0.0")));
    }
}
