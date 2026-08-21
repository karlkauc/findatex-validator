package com.findatex.validator.quickfeedback;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class QuickFeedbackClientTest {

    private HttpServer server;
    private String baseUrl;
    private volatile int status = 200;
    private volatile String responseBody = "{\"status\":\"ok\"}";
    private volatile String lastRequestBody;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/quick-feedback", ex -> {
            lastRequestBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void invalidRatingShortCircuitsWithoutCall() {
        assertThat(new QuickFeedbackClient().submit(baseUrl, 0, null, null))
                .isEqualTo(QuickFeedbackStatus.INVALID);
        assertThat(new QuickFeedbackClient().submit(baseUrl, 6, null, null))
                .isEqualTo(QuickFeedbackStatus.INVALID);
        assertThat(lastRequestBody).isNull();
    }

    @Test
    void overlongCommentShortCircuitsWithoutCall() {
        String comment = "x".repeat(QuickFeedbackEntry.MAX_COMMENT_LENGTH + 1);
        assertThat(new QuickFeedbackClient().submit(baseUrl, 3, comment, null))
                .isEqualTo(QuickFeedbackStatus.INVALID);
        assertThat(lastRequestBody).isNull();
    }

    @Test
    void blankEndpointIsUnavailable() {
        assertThat(new QuickFeedbackClient().submit("  ", 5, null, null))
                .isEqualTo(QuickFeedbackStatus.UNAVAILABLE);
    }

    @Test
    void parsesOkFromBodyAndSendsDesktopPayload() {
        status = 200;
        responseBody = "{\"status\":\"ok\"}";
        assertThat(new QuickFeedbackClient().submit(baseUrl, 4, "  nice tool  ", "TPT"))
                .isEqualTo(QuickFeedbackStatus.OK);
        assertThat(lastRequestBody)
                .contains("\"rating\":4")
                .contains("\"source\":\"desktop\"")
                .contains("\"comment\":\"nice tool\"")
                .contains("\"templateId\":\"TPT\"")
                .contains("\"appVersion\":");
    }

    @Test
    void omitsBlankCommentAndTemplateId() {
        status = 200;
        responseBody = "{\"status\":\"ok\"}";
        assertThat(new QuickFeedbackClient().submit(baseUrl, 5, "   ", "  "))
                .isEqualTo(QuickFeedbackStatus.OK);
        assertThat(lastRequestBody)
                .doesNotContain("\"comment\"")
                .doesNotContain("\"templateId\"");
    }

    @Test
    void parsesInvalidFromBodyOn400() {
        status = 400;
        responseBody = "{\"status\":\"invalid\"}";
        assertThat(new QuickFeedbackClient().submit(baseUrl, 5, null, null))
                .isEqualTo(QuickFeedbackStatus.INVALID);
    }

    @Test
    void unknownStatusTokenMapsToUnavailable() {
        status = 200;
        responseBody = "{\"status\":\"weird\"}";
        assertThat(new QuickFeedbackClient().submit(baseUrl, 5, null, null))
                .isEqualTo(QuickFeedbackStatus.UNAVAILABLE);
    }
}
