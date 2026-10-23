package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Persists quick-feedback (star rating) rows to Postgres via plain Agroal/JDBC.
 * Shares the usage-stats datasource and stays fully inert unless it is
 * configured: {@link #enabled()} gates every path so the app boots (and tests
 * run) with no DB. All DB failures are swallowed (rate-limited WARN, no
 * rethrow), so a broken DB never turns a rating into an error for the user.
 *
 * <p><b>Delivery.</b> The row is written on the request thread, before the
 * response — the same reasoning as {@link UsageStatsService}'s
 * {@code submitNow}: Cloud Run throttles an instance's CPU to near zero once
 * the response is sent, and with {@code --min-instances=0} a rating is
 * typically a lone POST with no follow-up request, so a background insert
 * would stall until the next visitor or be lost on scale-to-zero. The cost is
 * that the 200 waits for the insert (and, on a failing DB, for the retries).
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

    private volatile boolean warnedOnce;

    /**
     * Resilience mirroring {@link UsageStatsService}: a remote TLS Postgres
     * can drop a connection or time out an acquisition on a cold instance, so
     * a couple of retries let a later attempt land the row. Package-private so
     * the retry behaviour is unit-testable without a DB.
     */
    int maxInsertAttempts = 3;
    long retryBackoffMs = 1500;

    /** One insert attempt; throws so {@link #insertWithRetry} can retry it. */
    @FunctionalInterface
    interface SqlOp {
        void run() throws Exception;
    }

    /**
     * Off unless a datasource is configured <em>and</em> this is the
     * production instance ({@code FINDATEX_WEB_INSTANCE=prod}) — a dev or
     * load-test instance pointed at the production DB must not write to it.
     */
    public boolean enabled() {
        return config.usageStats().writesRows() && dataSource.isResolvable();
    }

    /** {@code country} derived server-side; {@code comment} already normalised. */
    public void record(int rating, String comment, String source, String appVersion,
                       String templateId, String country) {
        if (!enabled()) return;
        submitNow(new Row(source, rating, comment, appVersion, templateId, country));
    }

    /**
     * Writes the row on the calling (request) thread so it is committed before
     * the response leaves — see the class comment. Never throws.
     */
    private void submitNow(Row row) {
        try {
            insert(row);
        } catch (RuntimeException e) {
            log.debug("Quick-feedback synchronous insert rejected (ignored): {}", e.toString());
        }
    }

    /** Insert with retry; package-private seam so delivery tests can observe the thread. */
    void insert(Row row) {
        insertWithRetry(() -> doInsert(row));
    }

    /**
     * Runs {@code op}, retrying up to {@link #maxInsertAttempts} times with a
     * linear backoff so a stalled or dropped first attempt doesn't lose the
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
            ps.setString(4, cap(trimToNull(r.appVersion()), UsageRow.MAX_SHORT));
            ps.setString(5, cap(trimToNull(r.templateId()), UsageRow.MAX_SHORT));
            ps.setString(6, cap(trimToNull(r.country()), UsageRow.MAX_SHORT));
            ps.executeUpdate();
        }
        // Failures propagate to insertWithRetry, which retries then drops the
        // feedback with a rate-limited WARN — never disturbing the request path.
    }

    private static String cap(String s, int max) {
        return UsageRow.cap(s, max);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    record Row(String source, int rating, String comment,
                       String appVersion, String templateId, String country) {
    }
}
