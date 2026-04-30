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

    /**
     * Without an API key, OpenFIGI's /v3/mapping accepts at most 10 items per request
     * (otherwise it answers HTTP 413). Verify the client splits a >10 input list into
     * batches of at most 10. With an API key the limit is 100.
     */
    @Test
    void honoursPerRequestBatchLimitForKeyAndKeyless() throws Exception {
        java.util.List<Integer> seenChunkSizes = new java.util.concurrent.CopyOnWriteArrayList<>();
        HttpServer counting = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        counting.createContext("/v3/mapping", ex -> {
            byte[] body = ex.getRequestBody().readAllBytes();
            int items = (int) new String(body).chars().filter(ch -> ch == '{').count();
            seenChunkSizes.add(items);
            // Always echo back an empty data array per item to satisfy the parser.
            StringBuilder resp = new StringBuilder("[");
            for (int i = 0; i < items; i++) {
                resp.append(i == 0 ? "" : ",").append("{\"data\":[]}");
            }
            resp.append("]");
            byte[] out = resp.toString().getBytes();
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        counting.start();
        try {
            String b = "http://127.0.0.1:" + counting.getAddress().getPort();
            List<String> twentyFive = java.util.stream.IntStream.range(0, 25)
                    .mapToObj(i -> String.format("US%010d", i)).toList();

            OpenFigiClient noKey = new OpenFigiClient(new HttpExecutor(new RateLimiter(100, 10)), b, "");
            noKey.lookup(twentyFive);
            assertThat(seenChunkSizes).allMatch(n -> n <= OpenFigiClient.BATCH_NO_KEY);
            assertThat(seenChunkSizes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(25);

            seenChunkSizes.clear();
            OpenFigiClient withKey = new OpenFigiClient(
                    new HttpExecutor(new RateLimiter(100, 10)), b, "test-key");
            withKey.lookup(twentyFive);
            // 25 items with key fits in a single batch of <=100.
            assertThat(seenChunkSizes).hasSize(1);
            assertThat(seenChunkSizes.get(0)).isEqualTo(25);
        } finally {
            counting.stop(0);
        }
    }
}
