package com.tpt.validator.external.gleif;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpt.validator.external.http.HttpExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GleifClient {

    public static final String DEFAULT_BASE = "https://api.gleif.org";
    private static final int BATCH = 200;
    private static final ObjectMapper M = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(GleifClient.class);

    private final HttpExecutor http;
    private final String base;

    public GleifClient(HttpExecutor http) { this(http, DEFAULT_BASE); }

    GleifClient(HttpExecutor http, String base) {
        this.http = http;
        this.base = base;
    }

    /**
     * Returns one record per LEI that GLEIF knows. LEIs absent from the response
     * are simply not present in the map (caller treats absence as "unknown").
     */
    public Map<String, LeiRecord> lookup(List<String> leis) {
        Map<String, LeiRecord> out = new HashMap<>();
        for (int i = 0; i < leis.size(); i += BATCH) {
            List<String> chunk = leis.subList(i, Math.min(i + BATCH, leis.size()));
            String filter = URLEncoder.encode(String.join(",", chunk), StandardCharsets.UTF_8);
            URI uri = URI.create(base + "/api/v1/lei-records?filter%5Blei%5D=" + filter
                    + "&page%5Bsize%5D=" + chunk.size());
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/vnd.api+json")
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            http.send(req).ifPresent(r -> {
                if (r.statusCode() != 200) {
                    log.warn("GLEIF returned HTTP {} for batch starting {}", r.statusCode(), chunk.get(0));
                    return;
                }
                try {
                    JsonNode root = M.readTree(r.body());
                    for (JsonNode n : root.path("data")) {
                        LeiRecord rec = parse(n);
                        if (rec != null) out.put(rec.lei(), rec);
                    }
                } catch (Exception e) {
                    log.warn("GLEIF parse error: {}", e.getMessage());
                }
            });
        }
        return out;
    }

    private static LeiRecord parse(JsonNode n) {
        JsonNode a = n.path("attributes");
        if (a.isMissingNode()) return null;
        return new LeiRecord(
                a.path("lei").asText(""),
                a.path("entity").path("legalName").path("name").asText(""),
                a.path("entity").path("legalAddress").path("country").asText(""),
                a.path("entity").path("status").asText(""),
                a.path("registration").path("status").asText("")
        );
    }
}
