package com.findatex.validator.external.openfigi;

import com.sun.net.httpserver.HttpServer;
import com.findatex.validator.external.http.HttpExecutor;
import com.findatex.validator.external.http.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFigiClientTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/mapping", ex -> {
            try (InputStream in = getClass().getResourceAsStream("/external/openfigi-mapping-sample.json")) {
                byte[] body = in.readAllBytes();
                ex.getResponseHeaders().add("Content-Type", "application/json");
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
    void parsesHitAndMiss() {
        OpenFigiClient c = new OpenFigiClient(new HttpExecutor(new RateLimiter(100, 10)), base, "");
        Map<String, IsinRecord> out = c.lookup(List.of("US0378331005", "AAAAAAAAAAAA"));
        assertThat(out).containsOnlyKeys("US0378331005");
        IsinRecord apple = out.get("US0378331005");
        assertThat(apple.name()).isEqualTo("APPLE INC");
        assertThat(apple.currency()).isEqualTo("USD");
        assertThat(apple.exchCode()).isEqualTo("US");
        assertThat(apple.marketSector()).isEqualTo("Equity");
    }
}
