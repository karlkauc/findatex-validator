package com.findatex.validator.external.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class JsonCache<V> {

    private static final Logger log = LoggerFactory.getLogger(JsonCache.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

    public record Entry<V>(V value, long epochMilli) {}

    private final Path file;
    private final Duration ttl;
    private final TypeReference<Map<String, Entry<V>>> type;
    private final Map<String, Entry<V>> map = new ConcurrentHashMap<>();

    public JsonCache(Path file, Duration ttl, TypeReference<Map<String, Entry<V>>> type) {
        this.file = file;
        this.ttl = ttl;
        this.type = type;
        load();
    }

    public Optional<V> get(String key) {
        Entry<V> e = map.get(key);
        if (e == null) return Optional.empty();
        if (Instant.now().toEpochMilli() - e.epochMilli() > ttl.toMillis()) {
            map.remove(key);
            return Optional.empty();
        }
        return Optional.ofNullable(e.value());
    }

    public void put(String key, V value) {
        map.put(key, new Entry<>(value, Instant.now().toEpochMilli()));
    }

    public synchronized void flush() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            MAPPER.writeValue(tmp.toFile(), map);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Cache flush failed for {}: {}", file, e.getMessage());
        }
    }

    public void clear() {
        map.clear();
        flush();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            Map<String, Entry<V>> read = MAPPER.readValue(file.toFile(), type);
            if (read != null) map.putAll(read);
        } catch (IOException e) {
            log.warn("Cache load failed for {} ({}); starting fresh", file, e.getMessage());
        }
    }
}
