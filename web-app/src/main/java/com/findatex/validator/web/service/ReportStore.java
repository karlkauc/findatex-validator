package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
                .removalListener((UUID key, Path path, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    if (path != null) {
                        try {
                            Files.deleteIfExists(path);
                            log.debug("Evicted report {} ({}): file deleted", key, cause);
                        } catch (IOException e) {
                            log.warn("Could not delete evicted report {}: {}", path, e.toString());
                        }
                    }
                })
                .build();
    }

    public UUID store(Path file) {
        UUID id = UUID.randomUUID();
        cache.put(id, file);
        return id;
    }

    public Optional<Path> get(UUID id) {
        return Optional.ofNullable(cache.getIfPresent(id));
    }

    public void invalidate(UUID id) {
        cache.invalidate(id);
    }

    @PreDestroy
    void shutdown() {
        cache.invalidateAll();
        cache.cleanUp();
    }
}
