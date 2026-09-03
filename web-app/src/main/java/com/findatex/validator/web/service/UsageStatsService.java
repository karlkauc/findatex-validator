package com.findatex.validator.web.service;

import com.findatex.validator.stats.UsageEvent;
import com.findatex.validator.web.config.WebConfig;
import com.findatex.validator.web.dto.UsageStatsDto;
import io.agroal.api.AgroalDataSource;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persists anonymous usage events to Postgres via plain Agroal/JDBC. Stays
 * fully inert unless a datasource URL is configured: {@link #enabled()} gates
 * every path so the app boots (and tests run) with no DB. Writes are
 * fire-and-forget on a single-thread executor — never blocks the HTTP response
 * — and all DB failures are swallowed (rate-limited WARN, no rethrow).
 *
 * <p>Since 2026-09 every event kind lands in the one {@code usage_event}
 * table, told apart by {@code event_type}: validation runs (ok or failed),
 * report downloads, sample loads and page views. The desktop posts
 * {@link UsageStatsDto}s; the web layer records its own runs from
 * {@link ValidationOrchestrator} and the other kinds through the
 * {@code record…} helpers here.
 *
 * <p>The schema is created out-of-band (see docs/USAGE_STATS.md); this service
 * never issues DDL. Country / visitor hash / device come from a
 * {@link ClientContext}; the raw IP is never seen or logged here.
 *
 * <p><b>Delivery.</b> Cloud Run throttles an instance's CPU to near zero the
 * moment the response is sent, so a queued insert only progresses when the
 * <em>next</em> request arrives — and is lost if the instance is scaled to zero
 * first. For web-run events that is fine (the same user keeps clicking). For a
 * desktop ingest or a page-view beacon there is no next request: a lone POST,
 * then silence. Those two are therefore written on the request thread, before
 * the response ({@link #submitNow}); the caller is a fire-and-forget client
 * that never sees the latency.
 */
@ApplicationScoped
public class UsageStatsService {

    private static final Logger log = LoggerFactory.getLogger(UsageStatsService.class);

    private static final String INSERT = """
            INSERT INTO usage_event (
                event_type, status, client_event_at, install_id, source,
                app_version, os_name, java_major, visitor_hash, user_agent, device,
                template_id, template_version, profiles, mode, file_count,
                row_count, error_count, warning_count, info_count,
                overall_score, duration_ms, external_enabled, rule_ids,
                input_format, input_bytes, name_pattern, is_sample,
                ext_lookups, ext_cache_hits, ext_duration_ms, ext_errors,
                export_kind, path, referrer_host, campaign, country_code)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Inject
    WebConfig config;

    @Inject
    Instance<AgroalDataSource> dataSource;

    private ExecutorService worker;
    private volatile boolean warnedOnce;

    /**
     * Resilience for the fire-and-forget insert: Cloud Run throttles an
     * instance's CPU to near zero once the response is sent, so the worker
     * thread may be starved mid-insert and only make progress on a later
     * request; a remote TLS Postgres can also drop a connection in between.
     * Even with a generous {@code acquisition-timeout} a single attempt can
     * therefore fail, and a couple of retries let a later one land the row.
     * Package-private so the retry behaviour is unit-testable without a DB.
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

    // ---- desktop ------------------------------------------------------------

    /**
     * Desktop path: maps the posted DTO. Only the country is taken from
     * {@code ctx} — a desktop install is identified by its install id, so the
     * visitor hash / UA / device columns stay NULL.
     */
    public void record(UsageStatsDto dto, ClientContext ctx) {
        if (dto == null || !enabled()) return;
        UsageRow r = new UsageRow();
        r.eventType = dto.eventType() == null ? UsageEvent.TYPE_VALIDATE : dto.eventType();
        r.status = dto.status() == null ? UsageEvent.STATUS_OK : dto.status();
        r.clientEventAt = dto.clientEventAt();
        r.installId = dto.installId();
        r.source = "desktop";
        r.appVersion = dto.appVersion();
        r.osName = dto.osName();
        r.javaMajor = dto.javaMajor();
        r.templateId = dto.templateId();
        r.templateVersion = dto.templateVersion();
        r.profiles = dto.profiles();
        r.mode = dto.mode();
        r.fileCount = dto.fileCount();
        r.rowCount = dto.rowCount();
        r.errorCount = dto.errorCount();
        r.warningCount = dto.warningCount();
        r.infoCount = dto.infoCount();
        r.overallScore = dto.overallScore();
        r.durationMs = dto.durationMs();
        r.externalEnabled = dto.externalEnabled();
        r.ruleIds = dto.ruleIds();
        if (dto.input() != null) {
            r.inputFormat = dto.input().format();
            r.inputBytes = dto.input().bytes();
            r.namePattern = dto.input().namePattern();
        }
        if (dto.external() != null) {
            r.extLookups = dto.external().lookups();
            r.extCacheHits = dto.external().cacheHits();
            r.extDurationMs = dto.external().durationMs();
            r.extErrors = dto.external().errors();
        }
        r.isSample = dto.isSample();
        r.exportKind = dto.exportKind();
        r.country = ctx == null ? null : ctx.countryCode();
        submitNow(r);
    }

    // ---- web ----------------------------------------------------------------

    /**
     * Web run built by {@link ValidationOrchestrator}. {@code os_name} comes
     * from the browser's User-Agent in {@code ctx} (the event leaves it null).
     */
    public void record(UsageEvent ev, ClientContext ctx) {
        if (ev == null || !enabled()) return;
        UsageRow r = UsageRow.web(ev.eventType(), ev.status(), ctx);
        r.clientEventAt = ev.clientEventAt();
        r.appVersion = ev.appVersion();
        if (ev.osName() != null) r.osName = ev.osName();
        r.template(ev.templateId(), ev.templateVersion());
        r.profiles = ev.profiles();
        r.mode = ev.mode();
        r.fileCount = ev.fileCount();
        r.rowCount = ev.rowCount();
        r.errorCount = ev.errorCount();
        r.warningCount = ev.warningCount();
        r.infoCount = ev.infoCount();
        r.overallScore = ev.overallScore();
        r.durationMs = ev.durationMs();
        r.externalEnabled = ev.externalEnabled();
        r.ruleIds = ev.ruleIds();
        r.input(ev.input()).external(ev.external());
        r.isSample = ev.isSample();
        r.exportKind = ev.exportKind();
        submit(r);
    }

    /** Web validation attempt that produced no report; {@code status} is the failure class. */
    public void recordFailedRun(String status, String templateId, String templateVersion,
                                UsageEvent.Input input, Boolean isSample, Integer durationMs,
                                ClientContext ctx) {
        if (!enabled()) return;
        UsageRow r = UsageRow.web(UsageEvent.TYPE_VALIDATE, status, ctx)
                .template(templateId, templateVersion)
                .input(input);
        r.mode = "single";
        r.fileCount = 1;
        r.isSample = isSample;
        r.durationMs = durationMs;
        r.appVersion = appVersion();
        submit(r);
    }

    public void recordReportDownload(String templateId, String templateVersion, ClientContext ctx) {
        if (!enabled()) return;
        UsageRow r = UsageRow.web(UsageEvent.TYPE_REPORT_DOWNLOAD, UsageEvent.STATUS_OK, ctx)
                .template(templateId, templateVersion);
        r.mode = "single";
        r.fileCount = 1;
        r.exportKind = UsageEvent.EXPORT_XLSX;
        r.appVersion = appVersion();
        submit(r);
    }

    public void recordSampleLoad(String templateId, String templateVersion, ClientContext ctx) {
        if (!enabled()) return;
        UsageRow r = UsageRow.web(UsageEvent.TYPE_SAMPLE_LOAD, UsageEvent.STATUS_OK, ctx)
                .template(templateId, templateVersion);
        r.fileCount = 0;
        r.appVersion = appVersion();
        submit(r);
    }

    /** Page load; {@code path}/{@code referrerHost}/{@code campaign} already normalised. */
    public void recordPageView(String path, String referrerHost, String campaign, ClientContext ctx) {
        if (!enabled()) return;
        UsageRow r = UsageRow.web(UsageEvent.TYPE_PAGE_VIEW, UsageEvent.STATUS_OK, ctx);
        r.fileCount = 0;
        r.path = path;
        r.referrerHost = referrerHost;
        r.campaign = campaign;
        r.appVersion = appVersion();
        submitNow(r);
    }

    private static String appVersion() {
        String v = com.findatex.validator.AppInfo.version();
        return v == null || v.isBlank() ? null : v;
    }

    // ---- plumbing -----------------------------------------------------------

    private synchronized ExecutorService worker() {
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "usage-stats-db");
                t.setDaemon(true);
                return t;
            });
        }
        return worker;
    }

    /** Fire-and-forget: the single worker thread writes the row after the response. */
    private void submit(UsageRow row) {
        try {
            row.normalise();
            worker().submit(() -> insert(row));
        } catch (RuntimeException e) {
            log.debug("Usage-stats submit rejected (ignored): {}", e.toString());
        }
    }

    /**
     * Writes the row on the calling (request) thread so it is committed before
     * the response leaves — see the class comment on Cloud Run CPU throttling.
     * Same retry/swallow semantics as the worker path; never throws.
     */
    private void submitNow(UsageRow row) {
        try {
            row.normalise();
            insert(row);
        } catch (RuntimeException e) {
            log.debug("Usage-stats synchronous insert rejected (ignored): {}", e.toString());
        }
    }

    /** Insert with retry; package-private seam so delivery tests can observe the thread. */
    void insert(UsageRow row) {
        insertWithRetry(() -> doInsert(row));
    }

    /**
     * Runs {@code op}, retrying up to {@link #maxInsertAttempts} times with a
     * linear backoff so a stalled or dropped first attempt doesn't lose the
     * row. Never rethrows — a persistently failing DB drops the event with a
     * rate-limited WARN, exactly as before. Package-private for testing.
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
                        log.warn("Usage-stats insert failed after {} attempt(s) "
                                + "(further failures suppressed): {}", attempt, e.toString());
                    } else {
                        log.debug("Usage-stats insert failed after {} attempt(s) (ignored): {}",
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

    private void doInsert(UsageRow r) throws Exception {
        try (Connection c = dataSource.get().getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT)) {
            Array profiles = c.createArrayOf("text", toArray(r.profiles));
            Array rules = c.createArrayOf("text", toArray(r.ruleIds));
            try {
                Binder b = new Binder(ps);
                b.str(r.eventType);
                b.str(r.status);
                b.timestamp(r.clientEventAt);
                b.uuid(r.installId);
                b.str(r.source);
                b.str(trimToNull(r.appVersion));
                b.str(trimToNull(r.osName));
                b.integer(r.javaMajor);
                b.str(trimToNull(r.visitorHash));
                b.str(trimToNull(r.userAgent));
                b.str(r.device);
                b.str(trimToNull(r.templateId));
                b.str(trimToNull(r.templateVersion));
                b.array(profiles);
                b.str(r.mode);
                b.ps.setInt(b.next(), r.fileCount == null ? 1 : r.fileCount);
                b.integer(r.rowCount);
                b.integer(r.errorCount);
                b.integer(r.warningCount);
                b.integer(r.infoCount);
                b.numeric(r.overallScore);
                b.integer(r.durationMs);
                b.bool(r.externalEnabled);
                b.array(rules);
                b.str(r.inputFormat);
                b.bigint(r.inputBytes);
                b.str(r.namePattern);
                b.bool(r.isSample);
                b.integer(r.extLookups);
                b.integer(r.extCacheHits);
                b.integer(r.extDurationMs);
                b.integer(r.extErrors);
                b.str(r.exportKind);
                b.str(trimToNull(r.path));
                b.str(trimToNull(r.referrerHost));
                b.str(trimToNull(r.campaign));
                b.str(trimToNull(r.country));
                ps.executeUpdate();
            } finally {
                profiles.free();
                rules.free();
            }
        }
        // Failures propagate to insertWithRetry, which retries then drops the
        // event with a rate-limited WARN — never disturbing the request path.
    }

    /** Sequential parameter binding so the column list above stays the single source of order. */
    private static final class Binder {
        final PreparedStatement ps;
        private int idx;

        Binder(PreparedStatement ps) {
            this.ps = ps;
        }

        int next() {
            return ++idx;
        }

        void str(String v) throws SQLException {
            ps.setString(next(), v);
        }

        void integer(Integer v) throws SQLException {
            int i = next();
            if (v == null) ps.setNull(i, Types.INTEGER);
            else ps.setInt(i, v);
        }

        void bigint(Long v) throws SQLException {
            int i = next();
            if (v == null) ps.setNull(i, Types.BIGINT);
            else ps.setLong(i, v);
        }

        void bool(Boolean v) throws SQLException {
            int i = next();
            if (v == null) ps.setNull(i, Types.BOOLEAN);
            else ps.setBoolean(i, v);
        }

        void numeric(Double v) throws SQLException {
            int i = next();
            if (v == null) ps.setNull(i, Types.NUMERIC);
            else ps.setBigDecimal(i, java.math.BigDecimal.valueOf(v));
        }

        void array(Array a) throws SQLException {
            ps.setArray(next(), a);
        }

        void uuid(String v) throws SQLException {
            int i = next();
            try {
                ps.setObject(i, UUID.fromString(v));
            } catch (IllegalArgumentException | NullPointerException e) {
                // Defensive: a malformed install id shouldn't fail the insert path.
                ps.setObject(i, UUID.fromString(UsageEvent.WEB_INSTALL_ID));
            }
        }

        void timestamp(String iso) throws SQLException {
            int i = next();
            if (iso == null || iso.isBlank()) {
                ps.setNull(i, Types.TIMESTAMP_WITH_TIMEZONE);
                return;
            }
            try {
                ps.setObject(i, OffsetDateTime.ofInstant(Instant.parse(iso), ZoneOffset.UTC));
            } catch (RuntimeException e) {
                ps.setNull(i, Types.TIMESTAMP_WITH_TIMEZONE);
            }
        }
    }

    private static Object[] toArray(List<String> xs) {
        return xs == null ? new String[0] : xs.toArray(new String[0]);
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
}
