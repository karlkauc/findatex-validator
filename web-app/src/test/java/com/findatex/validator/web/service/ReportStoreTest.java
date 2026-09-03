package com.findatex.validator.web.service;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Quarkus) of the two-cache lifecycle: the XLSX is single-use, the
 * annotated-source JSON is readable until the TTL, both vanish on expiry.
 */
class ReportStoreTest {

    /** Manually advanced clock so TTL expiry is deterministic. */
    private static final class FakeTicker implements Ticker {
        private long nanos;
        @Override public long read() { return nanos; }
        void advance(Duration d) { nanos += d.toNanos(); }
    }

    @TempDir
    Path tmp;

    private Path file(String name) throws Exception {
        Path p = tmp.resolve(name);
        Files.writeString(p, "payload-" + name);
        return p;
    }

    @Test
    void takeRemovesXlsxEntryButKeepsAnnotatedReadable() throws Exception {
        FakeTicker ticker = new FakeTicker();
        ReportStore store = new ReportStore(5, ticker);
        Path xlsx = file("r.xlsx");
        Path json = file("r.json.gz");

        UUID id = store.store(xlsx, json, "TPT", "V7");

        assertThat(store.annotatedSource(id)).contains(json);
        assertThat(store.take(id)).isPresent();
        assertThat(store.take(id)).isEmpty();
        assertThat(store.get(id)).isEmpty();
        // The caller owns the XLSX after take(); the store must not have deleted it.
        assertThat(xlsx).exists();
        // Annotated source is independent of the download: readable repeatedly.
        assertThat(store.annotatedSource(id)).contains(json);
        assertThat(store.annotatedSource(id)).contains(json);
        assertThat(Files.readString(json)).isEqualTo("payload-r.json.gz");
    }

    @Test
    void ttlExpiryDeletesBothFiles() throws Exception {
        FakeTicker ticker = new FakeTicker();
        ReportStore store = new ReportStore(5, ticker);
        Path xlsx = file("e.xlsx");
        Path json = file("e.json.gz");
        UUID id = store.store(xlsx, json, "TPT", "V7");

        ticker.advance(Duration.ofMinutes(4));
        store.cleanUp();
        assertThat(store.get(id)).contains(xlsx);
        assertThat(store.annotatedSource(id)).contains(json);
        assertThat(xlsx).exists();
        assertThat(json).exists();

        ticker.advance(Duration.ofMinutes(2));
        store.cleanUp();
        assertThat(store.get(id)).isEmpty();
        assertThat(store.annotatedSource(id)).isEmpty();
        assertThat(xlsx).doesNotExist();
        assertThat(json).doesNotExist();
    }

    @Test
    void annotatedFileIsDeletedOnExpiryEvenAfterXlsxWasTaken() throws Exception {
        FakeTicker ticker = new FakeTicker();
        ReportStore store = new ReportStore(5, ticker);
        Path xlsx = file("t.xlsx");
        Path json = file("t.json.gz");
        UUID id = store.store(xlsx, json, "TPT", "V7");

        assertThat(store.take(id)).isPresent();
        ticker.advance(Duration.ofMinutes(6));
        store.cleanUp();
        assertThat(store.annotatedSource(id)).isEmpty();
        assertThat(json).doesNotExist();
        assertThat(xlsx).exists();   // still the caller's responsibility
    }

    @Test
    void storeWithoutAnnotatedLeavesNoSibling() throws Exception {
        FakeTicker ticker = new FakeTicker();
        ReportStore store = new ReportStore(5, ticker);
        Path xlsx = file("s.xlsx");

        UUID id = store.store(xlsx, null, "TPT", "V7");
        assertThat(store.annotatedSource(id)).isEmpty();
        assertThat(store.get(id)).contains(xlsx);

        UUID legacy = store.store(xlsx, "TPT", "V7");
        assertThat(store.annotatedSource(legacy)).isEmpty();

        ticker.advance(Duration.ofMinutes(6));
        store.cleanUp();
        assertThat(store.annotatedSource(id)).isEmpty();
        assertThat(xlsx).doesNotExist();
    }

    @Test
    void shutdownDeletesEverything() throws Exception {
        FakeTicker ticker = new FakeTicker();
        ReportStore store = new ReportStore(5, ticker);
        Path xlsx = file("d.xlsx");
        Path json = file("d.json.gz");
        UUID id = store.store(xlsx, json, "TPT", "V7");

        store.shutdown();
        assertThat(store.get(id)).isEmpty();
        assertThat(store.annotatedSource(id)).isEmpty();
        assertThat(json).doesNotExist();
        // invalidateAll() is an EXPLICIT removal for the XLSX cache — the existing take()
        // contract keeps those files; the OS tmp reaper handles shutdown leftovers.
    }
}
