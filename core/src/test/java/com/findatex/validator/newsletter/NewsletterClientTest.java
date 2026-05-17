package com.findatex.validator.newsletter;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NewsletterClientTest {

    private HttpServer server;
    private String baseUrl;
    private volatile int status = 200;
    private volatile String responseBody = "{\"status\":\"pending\"}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/newsletter/subscribe", ex -> {
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
    void invalidEmailShortCircuitsWithoutCall() {
        assertThat(new NewsletterClient().subscribe(baseUrl, "not-an-email"))
                .isEqualTo(NewsletterStatus.INVALID_EMAIL);
    }

    @Test
    void blankEndpointIsUnavailable() {
        assertThat(new NewsletterClient().subscribe("  ", "a@b.co"))
                .isEqualTo(NewsletterStatus.UNAVAILABLE);
    }

    @Test
    void parsesPendingFromBody() {
        status = 200;
        responseBody = "{\"status\":\"pending\"}";
        assertThat(new NewsletterClient().subscribe(baseUrl, "a@b.co"))
                .isEqualTo(NewsletterStatus.PENDING);
    }

    @Test
    void parsesInvalidEmailFromBodyOn400() {
        status = 400;
        responseBody = "{\"status\":\"invalid_email\"}";
        assertThat(new NewsletterClient().subscribe(baseUrl, "a@b.co"))
                .isEqualTo(NewsletterStatus.INVALID_EMAIL);
    }

    @Test
    void unknownStatusTokenMapsToUnavailable() {
        status = 200;
        responseBody = "{\"status\":\"weird\"}";
        assertThat(new NewsletterClient().subscribe(baseUrl, "a@b.co"))
                .isEqualTo(NewsletterStatus.UNAVAILABLE);
    }
}
