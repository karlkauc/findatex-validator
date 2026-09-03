package com.findatex.validator.web.service;

import com.findatex.validator.stats.UsageEvent;

import java.util.List;
import java.util.Set;

/**
 * One row of {@code usage_event}, whatever its {@code event_type}. A plain
 * mutable holder (package-private) so the few call sites can fill just the
 * columns that apply; every unset column is inserted as NULL. Web rows carry
 * the visitor-side columns from a {@link ClientContext}; desktop rows only its
 * country.
 */
final class UsageRow {

    static final Set<String> EVENT_TYPES = Set.of(
            UsageEvent.TYPE_VALIDATE, UsageEvent.TYPE_REPORT_DOWNLOAD,
            UsageEvent.TYPE_SAMPLE_LOAD, UsageEvent.TYPE_PAGE_VIEW);
    static final Set<String> STATUSES = Set.of(
            UsageEvent.STATUS_OK, UsageEvent.STATUS_UNSUPPORTED_TYPE, UsageEvent.STATUS_PARSE_ERROR,
            UsageEvent.STATUS_TEMPLATE_MISMATCH, UsageEvent.STATUS_BAD_REQUEST,
            UsageEvent.STATUS_RATE_LIMITED, UsageEvent.STATUS_TOO_LARGE, UsageEvent.STATUS_BUSY,
            UsageEvent.STATUS_ERROR);
    static final Set<String> FORMATS = Set.of("xlsx", "csv", "mixed");
    static final Set<String> PATTERNS = Set.of("dated_template", "template_token", "other");
    static final Set<String> EXPORT_KINDS = Set.of("xlsx", "per_file", "combined", "combined_annotated");
    static final Set<String> DEVICES = Set.of("desktop", "mobile", "bot", "unknown");

    String eventType = UsageEvent.TYPE_VALIDATE;
    String status = UsageEvent.STATUS_OK;
    String clientEventAt;
    String installId = UsageEvent.WEB_INSTALL_ID;
    String source = "web";
    String appVersion;
    String osName;
    Integer javaMajor;
    String visitorHash;
    String userAgent;
    String device;
    String templateId;
    String templateVersion;
    List<String> profiles = List.of();
    String mode;
    Integer fileCount;
    Integer rowCount;
    Integer errorCount;
    Integer warningCount;
    Integer infoCount;
    Double overallScore;
    Integer durationMs;
    Boolean externalEnabled;
    List<String> ruleIds = List.of();
    String inputFormat;
    Long inputBytes;
    String namePattern;
    Boolean isSample;
    Integer extLookups;
    Integer extCacheHits;
    Integer extDurationMs;
    Integer extErrors;
    String exportKind;
    String path;
    String referrerHost;
    String campaign;
    String country;

    /** Web event of the given type with the visitor columns taken from {@code ctx}. */
    static UsageRow web(String eventType, String status, ClientContext ctx) {
        UsageRow r = new UsageRow();
        r.eventType = eventType;
        r.status = status;
        r.source = "web";
        r.installId = UsageEvent.WEB_INSTALL_ID;
        r.visitor(ctx);
        return r;
    }

    UsageRow visitor(ClientContext ctx) {
        if (ctx == null) return this;
        country = ctx.countryCode();
        visitorHash = ctx.visitorHash();
        userAgent = ctx.userAgent();
        device = ctx.device();
        if (osName == null) osName = ctx.osName();
        return this;
    }

    UsageRow template(String id, String version) {
        templateId = id;
        templateVersion = version;
        return this;
    }

    UsageRow input(UsageEvent.Input in) {
        if (in == null) return this;
        inputFormat = in.format();
        inputBytes = in.bytes();
        namePattern = in.namePattern();
        return this;
    }

    UsageRow external(UsageEvent.External ext) {
        if (ext == null) return this;
        extLookups = ext.lookups();
        extCacheHits = ext.cacheHits();
        extDurationMs = ext.durationMs();
        extErrors = ext.errors();
        return this;
    }

    /**
     * Clamps every enumerated column to its CHECK list so one odd client value
     * can never fail the insert (and with it the retry budget) — the row is
     * worth more than the exact label.
     */
    UsageRow normalise() {
        eventType = oneOf(eventType, EVENT_TYPES, UsageEvent.TYPE_VALIDATE);
        status = oneOf(status, STATUSES, UsageEvent.STATUS_OK);
        inputFormat = oneOf(inputFormat, FORMATS, null);
        namePattern = oneOf(namePattern, PATTERNS, null);
        exportKind = oneOf(exportKind, EXPORT_KINDS, null);
        device = oneOf(device, DEVICES, null);
        if (mode != null && !mode.equals("single") && !mode.equals("batch")) mode = null;
        if (!"desktop".equals(source)) source = "web";
        return this;
    }

    private static String oneOf(String v, Set<String> allowed, String fallback) {
        return v != null && allowed.contains(v) ? v : fallback;
    }
}
