package com.tpt.validator.external.gleif;

import com.sun.net.httpserver.HttpServer;
import com.tpt.validator.external.http.HttpExecutor;
import com.tpt.validator.external.http.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GleifClientTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/lei-records", ex -> {
            try (InputStream in = getClass().getResourceAsStream("/external/gleif-records-sample.json")) {
                byte[] body = in.readAllBytes();
                ex.getResponseHeaders().add("Content-Type", "application/vnd.api+json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void parsesTwoRecords() {
        GleifClient c = new GleifClient(new HttpExecutor(new RateLimiter(100, 10)), base);
        Map<String, LeiRecord> out = c.lookup(List.of("529900D6BF99LW9R2E68", "5493001KJTIIGC8Y1R12"));
        assertThat(out).hasSize(2);
        assertThat(out.get("529900D6BF99LW9R2E68").legalName()).isEqualTo("SAP SE");
        assertThat(out.get("529900D6BF99LW9R2E68").country()).isEqualTo("DE");
        assertThat(out.get("529900D6BF99LW9R2E68").registrationStatus()).isEqualTo("ISSUED");
        assertThat(out.get("5493001KJTIIGC8Y1R12").registrationStatus()).isEqualTo("LAPSED");
    }
}
