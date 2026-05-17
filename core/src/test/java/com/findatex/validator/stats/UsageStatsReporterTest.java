package com.findatex.validator.stats;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.external.http.HttpExecutor;
import com.findatex.validator.external.http.RateLimiter;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class UsageStatsReporterTest {

    private UsageEvent sampleEvent() {
        return new UsageEvent("id-1", "desktop", "dev", "Linux", "TPT", "V7",
                java.util.List.of("EIOPA_QRT"), "single", 1, 10, 0, 0, 0,
                99.0, 5, false, java.util.List.of("XF-16"), "2026-05-17T00:00:00Z");
    }

    private UsageStatsReporter reporter(Supplier<AppSettings.UsageStats> cfg, int cap) {
        // Unroutable endpoint: the worker thread will block on connect timeout,
        // but report() must never wait on it.
        return new UsageStatsReporter(
                new HttpExecutor(new RateLimiter(1000, 1000)),
                cfg, "token", cap);
    }

    @Test
    void reportReturnsImmediatelyAndNeverThrowsEvenWhenEndpointDead() {
        UsageStatsReporter r = reporter(
                () -> new AppSettings.UsageStats(true, "id", "http://10.255.255.1:9/ingest"),
                1);
        long start = System.nanoTime();
        assertThatCode(() -> {
            for (int i = 0; i < 200; i++) r.report(sampleEvent());
        }).doesNotThrowAnyException();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        // 200 enqueues (queue cap 1, worker stuck on dead endpoint → overflow
        // dropped) must be effectively instant — proves non-blocking + no throw.
        assertThat(elapsedMs).isLessThan(2_000L);
    }

    @Test
    void disabledGateShortCircuits() {
        UsageStatsReporter r = reporter(
                () -> new AppSettings.UsageStats(false, "id", "http://example.invalid/ingest"),
                10);
        assertThatCode(() -> r.report(sampleEvent())).doesNotThrowAnyException();
    }

    @Test
    void blankEndpointShortCircuits() {
        UsageStatsReporter r = reporter(
                () -> new AppSettings.UsageStats(true, "id", ""),
                10);
        assertThatCode(() -> r.report(sampleEvent())).doesNotThrowAnyException();
    }

    @Test
    void blankTokenShortCircuits() {
        UsageStatsReporter r = new UsageStatsReporter(
                new HttpExecutor(new RateLimiter(1000, 1000)),
                () -> new AppSettings.UsageStats(true, "id", "http://example.invalid/ingest"),
                "", 10);
        assertThatCode(() -> r.report(sampleEvent())).doesNotThrowAnyException();
    }
}
