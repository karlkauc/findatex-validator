package com.findatex.validator.web.service;

import com.findatex.validator.stats.UsageEvent;
import com.findatex.validator.web.TestFixtures;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end through {@code POST /api/validate}: every failed run records a
 * {@code fail_reason} code next to its status — never the message the client
 * sees. {@link UsageStatsService} is replaced by a capturing subclass (no DB).
 */
@QuarkusTest
class FailReasonRecordingTest {

    static final class Capture extends UsageStatsService {
        final List<UsageRow> rows = new CopyOnWriteArrayList<>();

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        void insert(UsageRow row) {
            rows.add(row);
        }

        UsageRow awaitFailedRun() throws InterruptedException {
            for (int i = 0; i < 100; i++) {
                for (UsageRow r : rows) {
                    if (UsageEvent.TYPE_VALIDATE.equals(r.eventType) && !UsageEvent.STATUS_OK.equals(r.status)) {
                        return r;
                    }
                }
                Thread.sleep(50);
            }
            throw new AssertionError("no failed run recorded; rows=" + rows.size());
        }
    }

    Capture capture;

    @BeforeEach
    void install() {
        capture = new Capture();
        QuarkusMock.installMockForType(capture, UsageStatsService.class);
    }

    @Test
    void templateMismatchRecordsHeaderCountsNotHeaderTexts() throws Exception {
        given().multiPart("templateId", "TPT")
                .multiPart("templateVersion", "V8.0")
                .multiPart("file", "TPT_delivery.csv",
                        "Fund code;Fund name;Currency;Amount;Weight\nX;Y;EUR;1;2\n".getBytes())
                .when().post("/api/validate")
                .then().statusCode(400);
        UsageRow r = capture.awaitFailedRun();
        assertThat(r.status).isEqualTo(UsageEvent.STATUS_TEMPLATE_MISMATCH);
        assertThat(r.failReason).isEqualTo("header_mismatch:0/5");
    }

    @Test
    void unsupportedExtensionIsRecorded() throws Exception {
        given().multiPart("templateId", "TPT")
                .multiPart("file", "report.pdf", "%PDF-1.7".getBytes())
                .when().post("/api/validate")
                .then().statusCode(400);
        UsageRow r = capture.awaitFailedRun();
        assertThat(r.status).isEqualTo(UsageEvent.STATUS_UNSUPPORTED_TYPE);
        assertThat(r.failReason).isEqualTo("unsupported_extension");
    }

    @Test
    void xlsxWithoutZipSignatureIsRecorded() throws Exception {
        given().multiPart("templateId", "TPT")
                .multiPart("file", "delivery.xlsx", "not a zip at all".getBytes())
                .when().post("/api/validate")
                .then().statusCode(400);
        UsageRow r = capture.awaitFailedRun();
        assertThat(r.status).isEqualTo(UsageEvent.STATUS_PARSE_ERROR);
        assertThat(r.failReason).isEqualTo("missing_zip_signature");
    }

    @Test
    void badRequestNamesTheStepThatFailed() throws Exception {
        given().multiPart("templateId", "UFO")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then().statusCode(400);
        UsageRow r = capture.awaitFailedRun();
        assertThat(r.status).isEqualTo(UsageEvent.STATUS_BAD_REQUEST);
        assertThat(r.failReason).isEqualTo("invalid_template");

        capture.rows.clear();
        given().multiPart("templateId", "TPT")
                .multiPart("profiles", "BOGUS")
                .multiPart("file", TestFixtures.CLEAN_V7_XLSX.toFile())
                .when().post("/api/validate")
                .then().statusCode(400);
        assertThat(capture.awaitFailedRun().failReason).isEqualTo("invalid_profile");
    }
}
