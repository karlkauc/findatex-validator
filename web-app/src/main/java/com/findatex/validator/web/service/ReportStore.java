package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks the temporary XLSX report files produced by {@link ValidationOrchestrator}.
 * Files are evicted (and deleted from disk) after the configured TTL or after a single
 * successful download — whichever comes first. Datenschutz-relevant: nothing is persisted.
 */
@ApplicationScoped
public class ReportStore {

    private static final Logger log = LoggerFactory.getLogger(ReportStore.class);

    private final Cache<UUID, Path> cache;

    @Inject
    public ReportStore(WebConfig config) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(config.report().ttlMinutes()))
                .removalListener((UUID key, Path path, RemovalCause cause) -> {
                    if (path == null) return;
                    // EXPLICIT removals come from take() — the caller has taken
                    // ownership of the path and is responsible for deleting the
                    // file when its stream is fully written. Auto-deleting here
                    // would race the active reader and produce NoSuchFile errors.
                    if (cause == RemovalCause.EXPLICIT) {
                        log.debug("Report {} taken by caller — caller owns deletion", key);
                        return;
                    }
                    try {
                        Files.deleteIfExists(path);
                        log.debug("Evicted report {} ({}): file deleted", key, cause);
                    } catch (IOException e) {
                        log.warn("Could not delete evicted report {}: {}", path, e.toString());
                    }
                })
                .build();
    }

    public UUID store(Path file) {
        UUID id = UUID.randomUUID();
        cache.put(id, file);
        return id;
    }

    /**
     * Non-removing read — reserved for diagnostic / introspection callers.
     * Production download path uses {@link #take(UUID)} so single-use semantics
     * survive concurrent GETs.
     */
    public Optional<Path> get(UUID id) {
        return Optional.ofNullable(cache.getIfPresent(id));
    }

    /**
     * Atomically removes the entry and returns the path. The caller takes
     * ownership of the file and is responsible for deleting it after streaming
     * (the removal listener intentionally skips deletion for EXPLICIT removals
     * to avoid racing the reader). Two near-simultaneous calls for the same
     * UUID can never both succeed: exactly one {@code asMap().remove()} returns
     * the value, the other returns empty.
     */
    public Optional<Path> take(UUID id) {
        return Optional.ofNullable(cache.asMap().remove(id));
    }

    @PreDestroy
    void shutdown() {
        cache.invalidateAll();
        cache.cleanUp();
    }
}
