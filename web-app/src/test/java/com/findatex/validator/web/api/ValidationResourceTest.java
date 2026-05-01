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

    @Test
    void externalEnabledFormParamsAreAccepted() {
        // Default profile has external.enabled=false, so the orchestrator silently ignores
        // externalEnabled=true. The point of this test is that supplying the new form
        // parameters does not break the request shape.
        given()
                .multiPart("templateId", "TPT")
                .multiPart("templateVersion", "V7.0")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .multiPart("externalEnabled", "true")
                .multiPart("leiEnabled", "true")
                .multiPart("leiCheckLapsed", "true")
                .multiPart("isinEnabled", "true")
                .when().post("/api/validate")
                .then().statusCode(200);
    }

    @Test
    void userOpenfigiKeyDoesNotLeakIntoResponseBody() {
        String secretKey = "do-not-leak-this-key-1234";
        Response r = given()
                .multiPart("templateId", "TPT")
                .multiPart("templateVersion", "V7.0")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .multiPart("externalEnabled", "true")
                .multiPart("openfigiApiKey", secretKey)
                .when().post("/api/validate");

        r.then().statusCode(200);
        assertThat(r.asString()).doesNotContain(secretKey);
    }

    @Test
    void multiFundUploadIncludesPerFundScores() throws Exception {
        // Walk up to the project root samples/tpt/.
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        java.nio.file.Path samples = cwd.resolve("samples").resolve("tpt");
        if (!java.nio.file.Files.isDirectory(samples) && cwd.getParent() != null) {
            samples = cwd.getParent().resolve("samples").resolve("tpt");
        }
        java.nio.file.Path src = samples.resolve("13_multi_fund_with_errors.xlsx");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(src), "multi-fund fixture missing");

        given()
                .multiPart("templateId", "TPT")
                .multiPart("templateVersion", "V7.0")
                .multiPart("profiles", "SOLVENCY_II")
                .multiPart("file", src.toFile())
                .when().post("/api/validate")
                .then()
                .statusCode(200)
                .body("perFundScores.size()", org.hamcrest.Matchers.is(3))
                .body("perFundScores.portfolioId", org.hamcrest.Matchers.hasItems("FR0010000001", "DE0010000002", "LU0010000003"))
                .body("perFundScores[0].scores.size()", org.hamcrest.Matchers.greaterThan(0));
    }
}
