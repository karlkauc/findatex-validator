package com.findatex.validator.web.api;

import com.findatex.validator.web.TestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Lifecycle of the annotated-source side artifact: available after a validation, readable
 * repeatedly, independent of the single-use XLSX download.
 */
@QuarkusTest
class AnnotatedSourceLifecycleTest {

    private static Response validateBadFormats() {
        Response v = given()
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.BAD_FORMATS_XLSX.toFile())
                .when().post("/api/validate");
        v.then().statusCode(200);
        return v;
    }

    @Test
    void annotatedSourceIsReadManyAndSurvivesTheReportDownload() {
        Response v = validateBadFormats();
        assertThat(v.jsonPath().getBoolean("annotatedSourceAvailable")).isTrue();
        String id = v.path("reportId");

        given()
                .when().get("/api/annotated-source/" + id)
                .then()
                .statusCode(200)
                .header("Content-Encoding", "gzip")
                .header("Cache-Control", "private, no-store")
                .contentType("application/json")
                .body("rows.size()", greaterThan(0))
                .body("headers.size()", greaterThan(0))
                .body("headerRowIndex", equalTo(0));

        // Read-many: a second GET still works.
        given().when().get("/api/annotated-source/" + id).then().statusCode(200);

        // The XLSX download is single-use ...
        given().when().get("/api/report/" + id).then().statusCode(200);
        // ... but does not take the annotated source with it.
        given().when().get("/api/annotated-source/" + id).then().statusCode(200);
        given().when().get("/api/report/" + id).then().statusCode(404);
    }

    @Test
    void findingCellsIndexIntoTheResponseFindings() {
        Response v = validateBadFormats();
        JsonPath response = v.jsonPath();
        List<Map<String, Object>> findings = response.getList("findings");
        String id = response.getString("reportId");

        JsonPath doc = given()
                .when().get("/api/annotated-source/" + id)
                .then().statusCode(200)
                .extract().jsonPath();
        List<Map<String, Object>> rows = doc.getList("rows");
        List<List<Integer>> triples = doc.getList("findingCells");
        int headerRowIndex = doc.getInt("headerRowIndex");
        int width = doc.getList("headers").size();

        assertThat(triples).isNotEmpty();
        assertThat(rows.get(headerRowIndex).get("r")).isNull();
        boolean sawCellLevel = false;
        for (List<Integer> t : triples) {
            assertThat(t).hasSize(3);
            int f = t.get(0);
            int r = t.get(1);
            int c = t.get(2);
            assertThat(f).isBetween(0, findings.size() - 1);
            assertThat(r).isBetween(0, rows.size() - 1);
            assertThat(c).isBetween(0, width);
            Map<String, Object> finding = findings.get(f);
            assertThat(finding.get("rowIndex")).as("finding %d has a row", f).isNotNull();
            assertThat(rows.get(r).get("r")).isEqualTo(finding.get("rowIndex"));
            if (c > 0) {
                sawCellLevel = true;
                assertThat(finding.get("fieldNum")).as("cell-level finding %d has a field", f).isNotNull();
            }
        }
        assertThat(sawCellLevel).isTrue();
        // Every row carries exactly width cells.
        for (Map<String, Object> row : rows) {
            assertThat((List<?>) row.get("c")).hasSize(width);
        }
    }

    @Test
    void unknownAndMalformedIdsAre404() {
        given()
                .when().get("/api/annotated-source/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
        given()
                .when().get("/api/annotated-source/not-a-uuid")
                .then().statusCode(404);
    }
}
