package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import io.agroal.api.AgroalDataSource;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persists page views to Postgres via plain Agroal/JDBC, mirroring
 * {@link QuickFeedbackService}: shares the usage-stats datasource, stays fully
 * inert unless it is configured ({@link #enabled()} gates every path so the app
 * boots and tests run with no DB), writes fire-and-forget on a single-thread
 * executor, and swallows every DB failure (rate-limited WARN, no rethrow).
 *
 * <p>Why this exists: {@code usage_event} counts validation <i>runs</i>, so a
 * quiet week is ambiguous — nobody visited, or visitors arrived and left
 * without uploading anything. Those two call for opposite fixes (promotion vs.
 * a better landing page). Page views plus runs make the ratio visible.
 *
 * <p>Deliberately not a general analytics tool. No cookie, no local storage, no
 * IP, no fingerprint, no session or visitor id — the row cannot be tied to a
 * person or to another row, which is also why it needs no consent banner.
 * The referrer is reduced to its <b>host</b> here and the raw URL is never
 * stored or logged.
 *
 * <p>The schema is created out-of-band (see docs/USAGE_STATS.md); this service
 * never issues DDL.
 */
@ApplicationScoped
public class PageViewService {

    private static final Logger log = LoggerFactory.getLogger(PageViewService.class);

    private static final String INSERT = """
            INSERT INTO page_view (path, referrer_host, campaign, country_code)
            VALUES (?, ?, ?, ?)
            """;

    /** Generous caps: these columns exist to be grouped by, not to hold prose. */
    private static final int MAX_PATH = 200;
    private static final int MAX_HOST = 120;
    private static final int MAX_CAMPAIGN = 64;

    @Inject
    WebConfig config;

    @Inject
    Instance<AgroalDataSource> dataSource;

    private ExecutorService worker;
    private volatile boolean warnedOnce;

    /**
     * Resilience mirroring {@link UsageStatsService}: Cloud Run throttles the
     * instance's CPU once the response is sent, so the first attempt may stall
     * or fail; retries let a later attempt land the row. Package-private so the
     * retry behaviour is unit-testable without a DB.
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

    /**
     * Records one page load. {@code referrer} may be a full URL — only its host
     * survives. {@code country} is derived by the caller from the request IP.
     */
    public void record(String path, String referrer, String campaign, String country) {
        if (!enabled()) return;
        submit(new Row(
                normalisePath(path),
                hostOf(referrer),
                normaliseCampaign(campaign),
                trimToNull(country)));
    }

    /**
     * Path only: query string and fragment are cut off (they can carry search
     * terms or ids), and anything unreasonably long is truncated rather than
     * dropped so the view is still counted.
     */
    static String normalisePath(String raw) {
        if (raw == null || raw.isBlank()) return "/";
        String path = raw.trim();
        int cut = indexOfFirst(path, '?', '#');
        if (cut >= 0) path = path.substring(0, cut);
        if (path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        return path.length() > MAX_PATH ? path.substring(0, MAX_PATH) : path;
    }

    /**
     * Host of a referrer URL, lower-cased and without a leading {@code www.} so
     * {@code www.linkedin.com} and {@code linkedin.com} group together. Returns
     * {@code null} for anything unparseable — a referrer is a nice-to-have, and
     * a malformed one must never cost the page view.
     */
    static String hostOf(String referrer) {
        if (referrer == null || referrer.isBlank()) return null;
        try {
            URI uri = URI.create(referrer.trim());
            // Only real web referrers. android-app://, chrome-extension:// and
            // friends parse fine and would otherwise be recorded as "hosts"
            // that mean nothing in a traffic-source report.
            String scheme = uri.getScheme();
            if (scheme == null) return null;
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) return null;
            String host = uri.getHost();
            if (host == null || host.isBlank()) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            return host.length() > MAX_HOST ? host.substring(0, MAX_HOST) : host;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Campaign tag reduced to a safe slug. Anything outside
     * {@code [a-z0-9._-]} is dropped rather than escaped: this value is
     * grouped by and printed in reports, and it comes straight from a URL
     * anyone can craft.
     */
    static String normaliseCampaign(String raw) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : raw.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (sb.length() >= MAX_CAMPAIGN) break;
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static int indexOfFirst(String s, char a, char b) {
        int i = s.indexOf(a);
        int j = s.indexOf(b);
        if (i < 0) return j;
        if (j < 0) return i;
        return Math.min(i, j);
    }

    private synchronized ExecutorService worker() {
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "page-view-db");
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
            log.debug("Page-view submit rejected (ignored): {}", e.toString());
        }
    }

    /**
     * Runs {@code op}, retrying up to {@link #maxInsertAttempts} times with a
     * linear backoff so a stalled or dropped first attempt doesn't lose the
     * row. Never rethrows — a persistently failing DB drops the view with a
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
                        log.warn("Page-view insert failed after {} attempt(s) "
                                + "(further failures suppressed): {}", attempt, e.toString());
                    } else {
                        log.debug("Page-view insert failed after {} attempt(s) (ignored): {}",
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
            ps.setString(1, r.path());
            ps.setString(2, r.referrerHost());
            ps.setString(3, r.campaign());
            ps.setString(4, r.country());
            ps.executeUpdate();
        }
        // Failures propagate to insertWithRetry, which retries then drops the
        // view with a rate-limited WARN — never disturbing the request path.
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

    private record Row(String path, String referrerHost, String campaign, String country) {
    }
}
