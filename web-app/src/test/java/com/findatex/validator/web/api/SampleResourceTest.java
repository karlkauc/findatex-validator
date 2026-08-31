package com.findatex.validator.web.api;

import com.findatex.validator.web.service.SampleFiles;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * The "try an example" path end to end. The point of the feature is that a
 * visitor with no file to hand still sees a real result, so the assertions go
 * past "a file is served": each sample is pushed through {@code /api/validate}
 * at the version it is advertised with, and has to produce findings.
 *
 * <p>A sample that silently starts scoring 100 (regenerated as a clean file, or
 * advertised at the wrong spec version) would still "work" while demonstrating
 * nothing — that is the regression this guards.
 */
@QuarkusTest
class SampleResourceTest {

    @Test
    void everyTemplateAdvertisesItsSample() {
        given()
                .when().get("/api/templates")
                .then()
                .statusCode(200)
                .body("find { it.id == 'TPT' }.sample.url", is("/api/samples/TPT"))
                .body("find { it.id == 'TPT' }.sample.version", is("V7.0"))
                .body("find { it.id == 'EET' }.sample", notNullValue())
                .body("find { it.id == 'EMT' }.sample", notNullValue())
                .body("find { it.id == 'EPT' }.sample", notNullValue());
    }

    @Test
    void theAdvertisedVersionExistsForThatTemplate() {
        // The fixture is generated for one spec version; advertising a version
        // the template does not have would send the UI into a dead selection.
        Response templates = given().when().get("/api/templates");
        templates.then().statusCode(200);

        for (Map.Entry<String, SampleFiles.Sample> e : SampleFiles.declared().entrySet()) {
            String id = e.getKey();
            java.util.List<String> versions =
                    templates.path("find { it.id == '" + id + "' }.versions.version");
            assertThat(versions)
                    .as("declared sample version for " + id)
                    .contains(e.getValue().version());
        }
    }

    @Test
    void samplesDownloadAsXlsx() {
        for (String id : SampleFiles.declared().keySet()) {
            Response r = given().when().get("/api/samples/" + id);
            r.then().statusCode(200);
            assertThat(r.getHeader("Content-Disposition")).contains(".xlsx");
            byte[] body = r.getBody().asByteArray();
            // XLSX is a ZIP container: "PK\003\004".
            assertThat(body).hasSizeGreaterThan(1000);
            assertThat(new String(body, 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1))
                    .isEqualTo("PK");
        }
    }

    @Test
    void anUnknownTemplateIs404() {
        given().when().get("/api/samples/UFO").then().statusCode(404);
    }

    @Test
    void everySampleValidatesAtItsAdvertisedVersionAndShowsFindings() {
        for (SampleFiles.Sample sample : SampleFiles.declared().values()) {
            byte[] file = given().when().get("/api/samples/" + sample.templateId())
                    .then().statusCode(200).extract().body().asByteArray();

            given()
                    .multiPart("templateId", sample.templateId())
                    .multiPart("templateVersion", sample.version())
                    .multiPart("file", sample.filename(), file)
                    .when().post("/api/validate")
                    .then()
                    .statusCode(200)
                    .body("summary.templateId", is(sample.templateId()))
                    .body("summary.rowCount", greaterThan(0))
                    // A demo that finds nothing demonstrates nothing.
                    .body("summary.findingCount", greaterThan(0));
        }
    }
}
