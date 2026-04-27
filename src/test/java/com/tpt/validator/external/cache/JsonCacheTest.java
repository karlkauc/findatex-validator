package com.tpt.validator.external.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
