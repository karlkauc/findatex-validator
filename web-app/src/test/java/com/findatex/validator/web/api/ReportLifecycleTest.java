package com.findatex.validator.web.api;

import com.findatex.validator.web.TestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ReportLifecycleTest {

    @Test
    void downloadProducesXlsxThenSecondGetIs404() {
        Response v = given()
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate");
        v.then().statusCode(200);
        String id = v.path("reportId");

        // First download succeeds and returns a non-trivial XLSX (PK header).
        byte[] body = given()
                .when().get("/api/report/" + id)
                .then()
                .statusCode(200)
                .extract().asByteArray();
        assertThat(body.length).isGreaterThan(1024);
        assertThat(body[0]).isEqualTo((byte) 'P');
        assertThat(body[1]).isEqualTo((byte) 'K');

        // Second download for the same id is 404 (one-shot eviction).
        given()
                .when().get("/api/report/" + id)
                .then().statusCode(404);
    }

    @Test
    void unknownReportIdReturns404() {
        given()
                .when().get("/api/report/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    @Test
    void malformedReportIdReturns404() {
        given()
                .when().get("/api/report/not-a-uuid")
                .then().statusCode(404);
    }
}
