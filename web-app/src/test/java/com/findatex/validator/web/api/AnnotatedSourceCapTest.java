package com.findatex.validator.web.api;

import com.findatex.validator.web.TestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Over the configured size cap the run still produces the XLSX report but no
 * annotated-source side artifact.
 */
@QuarkusTest
@TestProfile(AnnotatedSourceCapTest.TinyCapProfile.class)
class AnnotatedSourceCapTest {

    public static class TinyCapProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // clean_v7.xlsx has 3 data rows — a cap of 1 row puts it over the limit.
            return Map.of("findatex.web.annotated-source.max-rows", "1");
        }
    }

    @Test
    void overCapRunHasNoAnnotatedSourceButStillAReport() {
        Response v = given()
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate");
        v.then().statusCode(200);
        assertThat(v.jsonPath().getBoolean("annotatedSourceAvailable")).isFalse();
        String id = v.path("reportId");

        given().when().get("/api/annotated-source/" + id).then().statusCode(404);
        given().when().get("/api/report/" + id).then().statusCode(200);
    }
}
