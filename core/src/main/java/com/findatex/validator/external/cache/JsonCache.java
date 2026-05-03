package com.findatex.validator.external.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.findatex.validator.config.PosixPerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class JsonCache<V> {

    private static final Logger log = LoggerFactory.getLogger(JsonCache.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
            // GLEIF/OpenFIGI lookup data isn't a secret, but it does reveal which
            // ISINs/LEIs were checked. Owner-only perms (0700 dir / 0600 file on
            // POSIX) keep that out of co-tenants' reach.
            PosixPerms.createOwnerOnlyParents(file);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            if (PosixPerms.posixAvailable()) {
                Files.deleteIfExists(tmp);
                Files.createFile(tmp, PosixFilePermissions.asFileAttribute(
                        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
            }
            MAPPER.writeValue(tmp.toFile(), map);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            PosixPerms.tightenToOwnerOnly(file);
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
