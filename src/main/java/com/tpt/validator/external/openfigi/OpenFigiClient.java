package com.tpt.validator.external.openfigi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpt.validator.external.http.HttpExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public final class OpenFigiClient {

    public static final String DEFAULT_BASE = "https://api.openfigi.com";
    private static final int BATCH = 100;
    private static final ObjectMapper M = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(OpenFigiClient.class);

    private final HttpExecutor http;
    private final String base;
    private final String apiKey;

    public OpenFigiClient(HttpExecutor http, String apiKey) { this(http, DEFAULT_BASE, apiKey); }

    OpenFigiClient(HttpExecutor http, String base, String apiKey) {
        this.http = http;
        this.base = base;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public Map<String, IsinRecord> lookup(List<String> isins,
                                          BooleanSupplier cancelled,
                                          IntConsumer onBatchDone) {
        Map<String, IsinRecord> out = new HashMap<>();
        for (int i = 0; i < isins.size(); i += BATCH) {
            if (cancelled.getAsBoolean()) break;
            List<String> chunk = isins.subList(i, Math.min(i + BATCH, isins.size()));
            try {
                String body = buildBody(chunk);
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + "/v3/mapping"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .POST(BodyPublishers.ofString(body));
                if (!apiKey.isEmpty()) b.header("X-OPENFIGI-APIKEY", apiKey);
                HttpRequest req = b.build();
                http.send(req).ifPresent(r -> {
                    if (r.statusCode() != 200) {
                        log.warn("OpenFIGI returned HTTP {} for batch starting {}", r.statusCode(), chunk.get(0));
                        return;
                    }
                    try {
                        JsonNode root = M.readTree(r.body());
                        if (!root.isArray() || root.size() != chunk.size()) {
                            log.warn("OpenFIGI response shape unexpected (size {})", root.size());
                            return;
                        }
                        for (int idx = 0; idx < chunk.size(); idx++) {
                            JsonNode item = root.get(idx);
                            JsonNode data = item.path("data");
                            if (data.isArray() && !data.isEmpty()) {
                                out.put(chunk.get(idx), parse(chunk.get(idx), data.get(0)));
                            }
                        }
                    } catch (Exception e) {
                        log.warn("OpenFIGI parse error: {}", e.getMessage());
                    }
                });
                onBatchDone.accept(Math.min(i + BATCH, isins.size()));
            } catch (Exception e) {
                log.warn("OpenFIGI request build error: {}", e.getMessage());
            }
        }
        return out;
    }

    /** Backwards-compatible overload: no cancel, no progress. */
    public Map<String, IsinRecord> lookup(List<String> isins) {
        return lookup(isins, () -> false, n -> {});
    }

    private static String buildBody(List<String> chunk) {
        List<Map<String, String>> items = new ArrayList<>();
        for (String isin : chunk) {
            items.add(Map.of("idType", "ID_ISIN", "idValue", isin));
        }
        try {
            return M.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("OpenFIGI body serialization failed", e);
        }
    }

    private static IsinRecord parse(String isin, JsonNode d) {
        return new IsinRecord(
                isin,
                d.path("figi").asText(""),
                d.path("name").asText(""),
                d.path("ticker").asText(""),
                d.path("exchCode").asText(""),
                d.path("marketSector").asText(""),
                d.path("securityType").asText(""),
                d.path("currency").asText(""));
    }
}
