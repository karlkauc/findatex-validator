package com.findatex.validator.web.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the retry seam in {@link QuickFeedbackService}. No DB or CDI
 * needed — exercises {@link QuickFeedbackService#insertWithRetry} directly with
 * an in-memory op so the retry behaviour is covered offline (mirrors
 * {@link UsageStatsServiceRetryTest}).
 */
class QuickFeedbackServiceRetryTest {

    private QuickFeedbackService newService() {
        QuickFeedbackService s = new QuickFeedbackService();
        s.retryBackoffMs = 0;     // no real sleeping in tests
        s.maxInsertAttempts = 3;
        return s;
    }

    @Test
    void succeedsOnFirstAttempt() {
        QuickFeedbackService s = newService();
        AtomicInteger calls = new AtomicInteger();
        s.insertWithRetry(() -> calls.incrementAndGet());
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retriesThenSucceeds() {
        QuickFeedbackService s = newService();
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
        QuickFeedbackService s = newService();
        AtomicInteger calls = new AtomicInteger();
        // Always fails: must exhaust exactly maxInsertAttempts and swallow.
        s.insertWithRetry(() -> {
            calls.incrementAndGet();
            throw new RuntimeException("db down");
        });
        assertThat(calls.get()).isEqualTo(3);
    }

    /**
     * Cloud Run throttles the CPU once the response is out and a rating is a
     * lone POST, so the row must be written on the request thread before
     * record() returns — a background worker would lose it on scale-to-zero
     * (mirrors {@link UsageStatsServiceDeliveryTest}).
     */
    @Test
    void recordInsertsOnTheCallingThreadBeforeReturning() {
        List<String> insertThreads = new CopyOnWriteArrayList<>();
        QuickFeedbackService s = new QuickFeedbackService() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            void insert(Row row) {
                insertThreads.add(Thread.currentThread().getName());
            }
        };
        s.record(5, "great", "web", "1.0.17", "TPT", "AT");
        // No waiting: the row must already be written when record() returns.
        assertThat(insertThreads).containsExactly(Thread.currentThread().getName());
    }

    @Test
    void aFailingInsertNeverReachesTheCaller() {
        QuickFeedbackService s = new QuickFeedbackService() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            void insert(Row row) {
                throw new IllegalStateException("datasource gone");
            }
        };
        // Must not throw: the resource answers 200 regardless, never 500.
        s.record(3, null, "web", null, null, null);
    }
}
