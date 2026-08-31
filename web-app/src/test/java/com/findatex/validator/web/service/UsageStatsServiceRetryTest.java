package com.findatex.validator.web.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the retry seam in {@link UsageStatsService}. No DB or CDI
 * needed — exercises {@link UsageStatsService#insertWithRetry} directly with an
 * in-memory op so the retry behaviour is covered offline.
 */
class UsageStatsServiceRetryTest {

    private UsageStatsService newService() {
        UsageStatsService s = new UsageStatsService();
        s.retryBackoffMs = 0;     // no real sleeping in tests
        s.maxInsertAttempts = 3;
        return s;
    }

    @Test
    void succeedsOnFirstAttempt() {
        UsageStatsService s = newService();
        AtomicInteger calls = new AtomicInteger();
        s.insertWithRetry(() -> calls.incrementAndGet());
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retriesThenSucceeds() {
        UsageStatsService s = newService();
        AtomicInteger calls = new AtomicInteger();
        // Fail on the first attempt (stalled/dropped connection), succeed on the second.
        s.insertWithRetry(() -> {
            if (calls.incrementAndGet() < 2) {
                throw new RuntimeException("Acquisition timeout while waiting for new connection");
            }
        });
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void givesUpAfterMaxAttemptsWithoutThrowing() {
        UsageStatsService s = newService();
        AtomicInteger calls = new AtomicInteger();
        // Always fails: must exhaust exactly maxInsertAttempts and swallow.
        s.insertWithRetry(() -> {
            calls.incrementAndGet();
            throw new RuntimeException("db down");
        });
        assertThat(calls.get()).isEqualTo(3);
    }
}
