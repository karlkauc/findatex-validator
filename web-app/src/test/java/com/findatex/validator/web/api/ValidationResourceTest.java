package com.findatex.validator.web.api;

import com.findatex.validator.web.TestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ValidationResourceTest {

    @Test
    void validateXlsxReturnsScoresAndFindings() {
        Response r = given()
                .multiPart("templateId", "TPT")
                .multiPart("templateVersion", "V7.0")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate");

        r.then().statusCode(200);
        String reportId = r.path("reportId");
        assertThat(UUID.fromString(reportId)).isNotNull();

        r.then()
                .body("summary.templateId", org.hamcrest.Matchers.is("TPT"))
                .body("summary.rowCount", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
                .body("summary.filename", org.hamcrest.Matchers.is("clean_v7.xlsx"))
                .body("scores.size()", org.hamcrest.Matchers.greaterThan(0))
                .body("findings", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void validateCsvAlsoWorks() {
        given()
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.MISSING_MANDATORY_CSV.toFile())
                .when().post("/api/validate")
                .then()
                .statusCode(200)
                .body("summary.filename", org.hamcrest.Matchers.is("missing_mandatory.csv"))
                .body("summary.errorCount", org.hamcrest.Matchers.greaterThan(0));
    }

    @Test
    void missingFilePartReturns400() {
        given()
                .multiPart("templateId", "TPT")
                .when().post("/api/validate")
                .then().statusCode(400);
    }

    @Test
    void unknownTemplateIdReturns400() {
        given()
                .multiPart("templateId", "UFO")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then().statusCode(400);
    }

    @Test
    void unknownProfileCodeReturns400() {
        given()
                .multiPart("templateId", "TPT")
                .multiPart("profiles", "BOGUS")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then().statusCode(400);
    }
}
