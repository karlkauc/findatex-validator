package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class TemplateResourceTest {

    @Test
    void listsAllFourTemplates() {
        given()
                .when().get("/api/templates")
                .then()
                .statusCode(200)
                .body("size()", is(4))
                .body("id", containsInAnyOrder("TPT", "EET", "EMT", "EPT"));
    }

    @Test
    void eachTemplateExposesAtLeastOneVersionWithProfiles() {
        given()
                .when().get("/api/templates")
                .then()
                .statusCode(200)
                .body("[0].versions.size()", greaterThan(0))
                .body("[0].versions[0].profiles", notNullValue());
    }

    @Test
    void tptHasAtLeastOneVersion() {
        given()
                .when().get("/api/templates")
                .then()
                .statusCode(200)
                .body("find { it.id == 'TPT' }.versions.size()", greaterThan(0))
                .body("find { it.id == 'TPT' }.versions[0].profiles.size()", greaterThan(0));
    }

    @Test
    void externalAvailableIsFalseByDefault() {
        given()
                .when().get("/api/templates")
                .then()
                .statusCode(200)
                .body("findAll { it.externalAvailable == false }.size()", is(4));
    }
}
