package com.findatex.validator.external.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.findatex.validator.external.gleif.LeiRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JsonCacheTest {

    record Demo(String value) {}

    @Test
    void putGetMissAfterTtl(@TempDir Path tmp) {
        JsonCache<Demo> c = new JsonCache<>(tmp.resolve("c.json"), Duration.ofMillis(10),
                new TypeReference<>() {});
        c.put("k", new Demo("v"));
        assertThat(c.get("k")).map(Demo::value).contains("v");
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        assertThat(c.get("k")).isEmpty();
    }

    @Test
    void persistsAcrossInstances(@TempDir Path tmp) {
        Path p = tmp.resolve("c.json");
        JsonCache<Demo> a = new JsonCache<>(p, Duration.ofDays(1), new TypeReference<>() {});
        a.put("k", new Demo("v"));
        a.flush();
        JsonCache<Demo> b = new JsonCache<>(p, Duration.ofDays(1), new TypeReference<>() {});
        assertThat(b.get("k")).map(Demo::value).contains("v");
    }

    /**
     * Round-trips a {@link LeiRecord} (which has an {@code isLapsed()} computed accessor).
     * Until {@code isLapsed} was annotated {@link com.fasterxml.jackson.annotation.JsonIgnore},
     * Jackson serialised it as a {@code "lapsed"} field which then broke deserialisation
     * because the canonical record constructor has no matching parameter — taking the whole
     * cache file with it.
     */
    @Test
    void roundTripsLeiRecordIncludingIsLapsedAccessor(@TempDir Path tmp) {
        Path p = tmp.resolve("lei.json");
        JsonCache<LeiRecord> a = new JsonCache<>(p, Duration.ofDays(1), new TypeReference<>() {});
        a.put("X", new LeiRecord("X", "ACME", "DE", "ACTIVE", "ISSUED"));
        a.flush();
        JsonCache<LeiRecord> b = new JsonCache<>(p, Duration.ofDays(1), new TypeReference<>() {});
        assertThat(b.get("X")).map(LeiRecord::lei).contains("X");
    }

    /**
     * Defence in depth: even if a previous version of the persisted schema wrote an
     * unknown property (or a future version will), the cache must still load — not be
     * silently discarded with a "starting fresh" warning.
     */
    @Test
    void survivesUnknownPropertyInPersistedFile(@TempDir Path tmp) throws Exception {
        Path p = tmp.resolve("lei.json");
        long now = java.time.Instant.now().toEpochMilli();
        String json = "{\n" +
                "  \"X\" : {\n" +
                "    \"value\" : { \"lei\" : \"X\", \"legalName\" : \"ACME\", \"country\" : \"DE\",\n" +
                "                  \"entityStatus\" : \"ACTIVE\", \"registrationStatus\" : \"ISSUED\",\n" +
                "                  \"lapsed\" : false },\n" +
                "    \"epochMilli\" : " + now + "\n" +
                "  }\n" +
                "}\n";
        Files.writeString(p, json);
        JsonCache<LeiRecord> c = new JsonCache<>(p, Duration.ofDays(1), new TypeReference<>() {});
        assertThat(c.get("X")).map(LeiRecord::legalName).contains("ACME");
    }
}
