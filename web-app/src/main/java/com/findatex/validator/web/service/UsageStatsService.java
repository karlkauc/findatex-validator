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
 * <p>The schema is created out-of-band (see docs/USAGE_STATS.md); this service
 * never issues DDL. {@code country_code} is supplied by the caller (derived
 * from the request IP); the raw IP is never seen or logged here.
 */
@ApplicationScoped
public class UsageStatsService {

    private static final Logger log = LoggerFactory.getLogger(UsageStatsService.class);

    private static final String INSERT = """
            INSERT INTO usage_event (
                client_event_at, install_id, source, app_version, os_name,
                template_id, template_version, profiles, mode, file_count,
                row_count, error_count, warning_count, info_count,
                overall_score, duration_ms, external_enabled, rule_ids, country_code)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Inject
    WebConfig config;

    @Inject
    Instance<AgroalDataSource> dataSource;

    private ExecutorService worker;
    private volatile boolean warnedOnce;

    public boolean enabled() {
        return config.usageStats().dbConfigured() && dataSource.isResolvable();
    }

    /** Desktop path: maps the posted DTO. {@code country} derived server-side. */
    public void record(UsageStatsDto dto, String source, String country) {
        if (dto == null || !enabled()) return;
        submit(new Row(
                dto.clientEventAt(), dto.installId(),
                source != null ? source : dto.source(),
                dto.appVersion(), dto.osName(), dto.templateId(), dto.templateVersion(),
                dto.profiles(), dto.mode(), dto.fileCount(), dto.rowCount(),
                dto.errorCount(), dto.warningCount(), dto.infoCount(),
                dto.overallScore(), dto.durationMs(), dto.externalEnabled(),
                dto.ruleIds(), country));
    }

    /** Web path: maps a server-built {@link UsageEvent}. */
    public void record(UsageEvent ev, String source, String country) {
        if (ev == null || !enabled()) return;
        submit(new Row(
                ev.clientEventAt(), ev.installId(),
                source != null ? source : ev.source(),
                ev.appVersion(), ev.osName(), ev.templateId(), ev.templateVersion(),
                ev.profiles(), ev.mode(), ev.fileCount(), ev.rowCount(),
                ev.errorCount(), ev.warningCount(), ev.infoCount(),
                ev.overallScore(), ev.durationMs(), ev.externalEnabled(),
                ev.ruleIds(), country));
    }

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

    private void submit(Row row) {
        try {
            worker().submit(() -> insert(row));
        } catch (RuntimeException e) {
            log.debug("Usage-stats submit rejected (ignored): {}", e.toString());
        }
    }

    private void insert(Row r) {
        try (Connection c = dataSource.get().getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT)) {

            setTimestamp(ps, 1, r.clientEventAt());
            setUuid(ps, 2, r.installId());
            ps.setString(3, r.source());
            ps.setString(4, trimToNull(r.appVersion()));
            ps.setString(5, trimToNull(r.osName()));
            ps.setString(6, r.templateId());
            ps.setString(7, r.templateVersion());
            Array profiles = c.createArrayOf("text", toArray(r.profiles()));
            ps.setArray(8, profiles);
            ps.setString(9, r.mode());
            ps.setInt(10, r.fileCount() == null ? 1 : r.fileCount());
            setInt(ps, 11, r.rowCount());
            setInt(ps, 12, r.errorCount());
            setInt(ps, 13, r.warningCount());
            setInt(ps, 14, r.infoCount());
            if (r.overallScore() == null) ps.setNull(15, Types.NUMERIC);
            else ps.setBigDecimal(15, java.math.BigDecimal.valueOf(r.overallScore()));
            setInt(ps, 16, r.durationMs());
            if (r.externalEnabled() == null) ps.setNull(17, Types.BOOLEAN);
            else ps.setBoolean(17, r.externalEnabled());
            Array rules = c.createArrayOf("text", toArray(r.ruleIds()));
            ps.setArray(18, rules);
            ps.setString(19, trimToNull(r.country()));

            ps.executeUpdate();
            profiles.free();
            rules.free();
            // Re-arm the WARN: a fresh failure after a recovered DB should be
            // visible again, while a continuous failure streak stays quiet.
            warnedOnce = false;
        } catch (Exception e) {
            // DB down / schema missing / bad row — drop silently, never disturb
            // the request path. Rate-limit the WARN so a dead DB can't flood logs.
            if (!warnedOnce) {
                warnedOnce = true;
                log.warn("Usage-stats insert failed (further failures suppressed): {}", e.toString());
            } else {
                log.debug("Usage-stats insert failed (ignored): {}", e.toString());
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

    private static void setInt(PreparedStatement ps, int idx, Integer v) throws java.sql.SQLException {
        if (v == null) ps.setNull(idx, Types.INTEGER);
        else ps.setInt(idx, v);
    }

    private static void setUuid(PreparedStatement ps, int idx, String v) throws java.sql.SQLException {
        try {
            ps.setObject(idx, UUID.fromString(v));
        } catch (IllegalArgumentException | NullPointerException e) {
            // Defensive: a malformed install id shouldn't fail the insert path.
            ps.setObject(idx, UUID.fromString(UsageEvent.WEB_INSTALL_ID));
        }
    }

    private static void setTimestamp(PreparedStatement ps, int idx, String iso) throws java.sql.SQLException {
        if (iso == null || iso.isBlank()) {
            ps.setNull(idx, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        try {
            ps.setObject(idx, OffsetDateTime.ofInstant(Instant.parse(iso), ZoneOffset.UTC));
        } catch (RuntimeException e) {
            ps.setNull(idx, Types.TIMESTAMP_WITH_TIMEZONE);
        }
    }

    @PreDestroy
    void shutdown() {
        if (worker != null) worker.shutdownNow();
    }

    private record Row(String clientEventAt, String installId, String source,
                       String appVersion, String osName, String templateId,
                       String templateVersion, List<String> profiles, String mode,
                       Integer fileCount, Integer rowCount, Integer errorCount,
                       Integer warningCount, Integer infoCount, Double overallScore,
                       Integer durationMs, Boolean externalEnabled,
                       List<String> ruleIds, String country) {
    }
}
