package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import io.agroal.api.AgroalDataSource;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persists quick-feedback (star rating) rows to Postgres via plain Agroal/JDBC.
 * Shares the usage-stats datasource and stays fully inert unless it is
 * configured: {@link #enabled()} gates every path so the app boots (and tests
 * run) with no DB. The submission is user-initiated, but persistence is still
 * asynchronous on a single-thread executor — Neon cold starts (10-30&nbsp;s)
 * must never block the HTTP response. All DB failures are swallowed
 * (rate-limited WARN, no rethrow).
 *
 * <p>The schema is created out-of-band (see docs/QUICK_FEEDBACK.md); this
 * service never issues DDL. {@code country_code} is supplied by the caller
 * (derived from the request IP); the raw IP is never seen or logged here.
 * The comment text is stored but never logged.
 */
@ApplicationScoped
public class QuickFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(QuickFeedbackService.class);

    private static final String INSERT = """
            INSERT INTO quick_feedback (
                source, rating, comment, app_version, template_id, country_code)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    @Inject
    WebConfig config;

    @Inject
    Instance<AgroalDataSource> dataSource;

    private ExecutorService worker;
    private volatile boolean warnedOnce;

    /**
     * Cold-start resilience, mirroring {@link UsageStatsService}: Neon suspends
     * its compute when idle, so the first attempt may fail while the wake is in
     * progress; retries let a later attempt land the row. Package-private so
     * the retry behaviour is unit-testable without a DB.
     */
    int maxInsertAttempts = 3;
    long retryBackoffMs = 1500;

    /** One insert attempt; throws so {@link #insertWithRetry} can retry it. */
    @FunctionalInterface
    interface SqlOp {
        void run() throws Exception;
    }

    public boolean enabled() {
        return config.usageStats().dbConfigured() && dataSource.isResolvable();
    }

    /** {@code country} derived server-side; {@code comment} already normalised. */
    public void record(int rating, String comment, String source, String appVersion,
                       String templateId, String country) {
        if (!enabled()) return;
        submit(new Row(source, rating, comment, appVersion, templateId, country));
    }

    private synchronized ExecutorService worker() {
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "quick-feedback-db");
                t.setDaemon(true);
                return t;
            });
        }
        return worker;
    }

    private void submit(Row row) {
        try {
            worker().submit(() -> insertWithRetry(() -> doInsert(row)));
        } catch (RuntimeException e) {
            log.debug("Quick-feedback submit rejected (ignored): {}", e.toString());
        }
    }

    /**
     * Runs {@code op}, retrying up to {@link #maxInsertAttempts} times with a
     * linear backoff so a cold-Neon wake on the first attempt doesn't lose the
     * row. Never rethrows — a persistently failing DB drops the feedback with a
     * rate-limited WARN. Package-private for testing.
     */
    void insertWithRetry(SqlOp op) {
        for (int attempt = 1; ; attempt++) {
            try {
                op.run();
                // Success: re-arm the WARN so a later failure after a recovered
                // DB is visible again, while a continuous streak stays quiet.
                warnedOnce = false;
                return;
            } catch (Exception e) {
                if (attempt >= maxInsertAttempts) {
                    if (!warnedOnce) {
                        warnedOnce = true;
                        log.warn("Quick-feedback insert failed after {} attempt(s) "
                                + "(further failures suppressed): {}", attempt, e.toString());
                    } else {
                        log.debug("Quick-feedback insert failed after {} attempt(s) (ignored): {}",
                                attempt, e.toString());
                    }
                    return;
                }
                if (!backoff(attempt)) return; // interrupted (shutdown) — abandon
            }
        }
    }

    /** Linear backoff between retries; returns false if interrupted. */
    private boolean backoff(int attempt) {
        long ms = retryBackoffMs * attempt;
        if (ms <= 0) return true;
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void doInsert(Row r) throws Exception {
        try (Connection c = dataSource.get().getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT)) {
            ps.setString(1, r.source());
            ps.setInt(2, r.rating());
            ps.setString(3, trimToNull(r.comment()));
            ps.setString(4, trimToNull(r.appVersion()));
            ps.setString(5, trimToNull(r.templateId()));
            ps.setString(6, trimToNull(r.country()));
            ps.executeUpdate();
        }
        // Failures propagate to insertWithRetry, which retries then drops the
        // feedback with a rate-limited WARN — never disturbing the request path.
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @PreDestroy
    void shutdown() {
        if (worker != null) worker.shutdownNow();
    }

    private record Row(String source, int rating, String comment,
                       String appVersion, String templateId, String country) {
    }
}
