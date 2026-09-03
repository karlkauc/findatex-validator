package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Ticker;
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
 * Tracks the temporary files produced by {@link ValidationOrchestrator} per validation run:
 * the XLSX report and — when the upload was small enough — the gzip-JSON annotated-source
 * side artifact, both under the same UUID.
 *
 * <p>The XLSX is evicted (and deleted from disk) after the configured TTL or after a single
 * successful download — whichever comes first. The annotated source is a read-many view:
 * it lives until the TTL regardless of whether the report was downloaded, and is deleted on
 * every removal. Datenschutz-relevant: nothing is persisted beyond the TTL.</p>
 */
@ApplicationScoped
public class ReportStore {

    private static final Logger log = LoggerFactory.getLogger(ReportStore.class);

    private final Cache<UUID, Entry> cache;
    private final Cache<UUID, Path> annotatedSources;

    /**
     * What the store knows about a report: the temp file plus the template it
     * was produced for, so the download can be attributed in the usage stats.
     */
    public record Entry(Path path, String templateId, String templateVersion) {
    }

    @Inject
    public ReportStore(WebConfig config) {
        this(config.report().ttlMinutes(), Ticker.systemTicker());
    }

    /** Test seam: same wiring with an injectable clock so TTL expiry is deterministic. */
    ReportStore(int ttlMinutes, Ticker ticker) {
        Duration ttl = Duration.ofMinutes(ttlMinutes);
        // Same-thread executor: removal listeners (the file deletes) run inside the
        // maintenance that evicts the entry, so an expired entry never leaves its file
        // behind for a while on the common pool — and cleanUp() is deterministic in tests.
        // The listeners only unlink a tempfile, so the inline cost is negligible.
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .ticker(ticker)
                .executor(Runnable::run)
                .removalListener((UUID key, Entry entry, RemovalCause cause) -> {
                    if (entry == null || entry.path() == null) return;
                    Path path = entry.path();
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
        // The annotated source is served whole (Files.readAllBytes) from a non-removing
        // lookup, so no reader can hold the path after removal — delete on every cause.
        this.annotatedSources = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .ticker(ticker)
                .executor(Runnable::run)
                .removalListener((UUID key, Path path, RemovalCause cause) -> {
                    if (path == null) return;
                    try {
                        Files.deleteIfExists(path);
                        log.debug("Evicted annotated source {} ({}): file deleted", key, cause);
                    } catch (IOException e) {
                        log.warn("Could not delete evicted annotated source {}: {}", path, e.toString());
                    }
                })
                .build();
    }

    public UUID store(Path file) {
        return store(file, null, null, null);
    }

    public UUID store(Path file, String templateId, String templateVersion) {
        return store(file, null, templateId, templateVersion);
    }

    /**
     * Registers a run's artifacts under one fresh UUID.
     *
     * @param annotatedOrNull the gzip-JSON annotated-source file, or {@code null} when the
     *                        run produced none (over the size cap, or the write failed)
     */
    public UUID store(Path file, Path annotatedOrNull, String templateId, String templateVersion) {
        UUID id = UUID.randomUUID();
        cache.put(id, new Entry(file, templateId, templateVersion));
        if (annotatedOrNull != null) {
            annotatedSources.put(id, annotatedOrNull);
        }
        return id;
    }

    /**
     * Non-removing read — reserved for diagnostic / introspection callers.
     * Production download path uses {@link #take(UUID)} so single-use semantics
     * survive concurrent GETs.
     */
    public Optional<Path> get(UUID id) {
        return Optional.ofNullable(cache.getIfPresent(id)).map(Entry::path);
    }

    /**
     * Non-removing read of the annotated-source side artifact. Unlike the XLSX this is
     * read-many: the SPA may open the view several times within the TTL, before or after
     * downloading the report. Empty when the run produced none or the TTL has passed.
     */
    public Optional<Path> annotatedSource(UUID id) {
        return Optional.ofNullable(annotatedSources.getIfPresent(id));
    }

    /**
     * Atomically removes the entry and returns the path. The caller takes
     * ownership of the file and is responsible for deleting it after streaming
     * (the removal listener intentionally skips deletion for EXPLICIT removals
     * to avoid racing the reader). Two near-simultaneous calls for the same
     * UUID can never both succeed: exactly one {@code asMap().remove()} returns
     * the value, the other returns empty.
     *
     * <p>Only the XLSX entry is taken — the annotated source stays until the TTL.</p>
     */
    public Optional<Entry> take(UUID id) {
        return Optional.ofNullable(cache.asMap().remove(id));
    }

    /** Runs pending expiry work on both caches (tests; Caffeine otherwise does this lazily). */
    void cleanUp() {
        cache.cleanUp();
        annotatedSources.cleanUp();
    }

    @PreDestroy
    void shutdown() {
        cache.invalidateAll();
        cache.cleanUp();
        annotatedSources.invalidateAll();
        annotatedSources.cleanUp();
    }
}
