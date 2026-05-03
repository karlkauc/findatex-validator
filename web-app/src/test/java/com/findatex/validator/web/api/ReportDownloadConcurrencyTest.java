package com.findatex.validator.web.api;

import com.findatex.validator.web.TestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the TOCTOU race the audit caught: previously, two near-simultaneous
 * GETs on the same report UUID could both observe the cache entry and both stream
 * the file before the post-stream {@code invalidate()} fired. {@link
 * com.findatex.validator.web.service.ReportStore#take(java.util.UUID)} now removes
 * the entry atomically, so exactly one request wins.
 */
@QuarkusTest
class ReportDownloadConcurrencyTest {

    @Test
    void concurrentGetsForSameIdYieldExactlyOne200AndOne404() throws Exception {
        Response v = given()
                .multiPart("templateId", "TPT")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate");
        v.then().statusCode(200);
        String id = v.path("reportId");

        // Fire two parallel downloads and gather their HTTP statuses.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> download = () -> given()
                    .when().get("/api/report/" + id)
                    .then().extract().statusCode();
            Future<Integer> a = pool.submit(download);
            Future<Integer> b = pool.submit(download);

            int statusA = a.get(30, TimeUnit.SECONDS);
            int statusB = b.get(30, TimeUnit.SECONDS);

            // Exactly one wins (200), exactly one loses (404). Order is irrelevant.
            int min = Math.min(statusA, statusB);
            int max = Math.max(statusA, statusB);
            assertThat(min).isEqualTo(200);
            assertThat(max).isEqualTo(404);
        } finally {
            pool.shutdownNow();
        }
    }
}
