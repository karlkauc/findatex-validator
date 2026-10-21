package com.findatex.validator.stats;

import com.findatex.validator.AppInfo;

import com.findatex.validator.batch.BatchResult;
import com.findatex.validator.batch.BatchSummary;
import com.findatex.validator.config.AppSettings;
import com.findatex.validator.ingest.TemplateMismatchException;
import com.findatex.validator.report.QualityReport;
import com.findatex.validator.report.ScoreCategory;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Anonymous, aggregate-only record of one usage event: a validation run
 * (successful or failed), a report export/download, a sample-file load, or —
 * desktop only — an app start / daily heartbeat (so installations that never
 * validate anything still count as active).
 * Carries deliberately coarse counts, classes and identifiers — never file
 * names, paths, fund names, ISIN/LEI/codes, cell values, or
 * {@code Finding.message()/value()}. File-related attributes are reduced to
 * non-identifying classes by {@link FileNameShape} (format, size, whether the
 * name follows the FinDatEx naming pattern). The server derives
 * {@code country_code} (and, for web runs, the visitor hash / device / OS
 * family) from the request; the raw IP is never part of this event and never
 * leaves the desktop.
 *
 * <p>{@code ruleIds} lists every triggered rule id; {@code errorRuleIds} /
 * {@code warningRuleIds} split them by {@link Finding#severity()} (INFO-only
 * rules appear in {@code ruleIds} alone). Ids only, never messages or values.
 *
 * <p>{@code failReason} (failed runs only) is a short <em>code</em> that
 * refines {@code status} — e.g. {@code header_mismatch:0/57} or the simple
 * class name of the exception — never a message (see {@link #sanitizeFailReason(String)}).
 *
 * <p>{@code overallScore} is the OVERALL category scaled to a 0..100 percentage
 * with two decimals (the scorer works on a 0..1 fraction).
 *
 * <p>Old desktop builds post the pre-2026-09 shape without {@code eventType},
 * {@code status}, {@code input}, {@code external} …; the web ingest treats every
 * missing field as {@code validate}/{@code ok}/null, so the wire format is
 * backward compatible.
 */
public record UsageEvent(
        String eventType,
        String status,
        String installId,
        String source,
        String appVersion,
        String osName,
        Integer javaMajor,
        String templateId,
        String templateVersion,
        List<String> profiles,
        String mode,
        int fileCount,
        Integer rowCount,
        Integer errorCount,
        Integer warningCount,
        Integer infoCount,
        Double overallScore,
        Integer durationMs,
        Boolean externalEnabled,
        List<String> ruleIds,
        List<String> errorRuleIds,
        List<String> warningRuleIds,
        Input input,
        External external,
        Boolean isSample,
        String exportKind,
        String clientEventAt,
        String failReason) {

    /** Sentinel install id used for runs that originate in the web layer. */
    public static final String WEB_INSTALL_ID = "00000000-0000-0000-0000-000000000000";

    // ---- event_type values -------------------------------------------------
    public static final String TYPE_VALIDATE = "validate";
    public static final String TYPE_REPORT_DOWNLOAD = "report_download";
    public static final String TYPE_SAMPLE_LOAD = "sample_load";
    public static final String TYPE_PAGE_VIEW = "page_view";
    /** Desktop: the app was started (once per launch, after the main window is shown). */
    public static final String TYPE_APP_START = "app_start";
    /** Desktop: the app is still running — sent every 24 h after the start. */
    public static final String TYPE_HEARTBEAT = "heartbeat";

    // ---- status values (never a message) -----------------------------------
    public static final String STATUS_OK = "ok";
    public static final String STATUS_UNSUPPORTED_TYPE = "unsupported_type";
    public static final String STATUS_PARSE_ERROR = "parse_error";
    public static final String STATUS_TEMPLATE_MISMATCH = "template_mismatch";
    public static final String STATUS_BAD_REQUEST = "bad_request";
    public static final String STATUS_RATE_LIMITED = "rate_limited";
    public static final String STATUS_TOO_LARGE = "too_large";
    public static final String STATUS_BUSY = "busy";
    public static final String STATUS_ERROR = "error";

    /** Upper bound of a {@code fail_reason} code. */
    public static final int MAX_FAIL_REASON = 100;

    // ---- export_kind values ------------------------------------------------
    public static final String EXPORT_XLSX = "xlsx";
    public static final String EXPORT_PER_FILE = "per_file";
    public static final String EXPORT_COMBINED = "combined";
    public static final String EXPORT_COMBINED_ANNOTATED = "combined_annotated";

    /**
     * Derived, non-identifying attributes of the validated input. Built only
     * through {@link FileNameShape} — never carries the name itself.
     *
     * @param format      {@code xlsx}, {@code csv} or {@code mixed} (batch)
     * @param bytes       size in bytes (sum for a batch); null if unknown
     * @param namePattern {@code dated_template}, {@code template_token} or {@code other}
     */
    public record Input(String format, Long bytes, String namePattern) {
        public static final Input UNKNOWN = new Input(null, null, null);
    }

    /**
     * Aggregate figures of the optional GLEIF / OpenFIGI phase of one run.
     *
     * @param lookups    remote requests issued (distinct keys not served from cache)
     * @param cacheHits  distinct keys served from the local cache
     * @param durationMs wall-clock of the online phase; null when not measured
     * @param errors     number of unavailable / cancelled phases
     */
    public record External(Integer lookups, Integer cacheHits, Integer durationMs, Integer errors) {
    }

    /** Single-file desktop run. */
    public static UsageEvent from(QualityReport report,
                                  TemplateDefinition template,
                                  TemplateVersion version,
                                  AppSettings settings,
                                  String mode,
                                  long durationMs,
                                  Input input,
                                  External external) {
        List<Finding> findings = report.findings();
        return new UsageEvent(
                TYPE_VALIDATE,
                STATUS_OK,
                settings.usageStats().installId(),
                "desktop",
                detectAppVersion(),
                osFamily(),
                detectJavaMajor(),
                template.id().name(),
                version.version(),
                profileCodes(report.activeProfiles()),
                mode,
                1,
                report.file().rows().size(),
                countSeverity(findings, Severity.ERROR),
                countSeverity(findings, Severity.WARNING),
                countSeverity(findings, Severity.INFO),
                overallPercent(report.scores().get(ScoreCategory.OVERALL)),
                clampMs(durationMs),
                settings.external().enabled(),
                ruleIds(findings),
                ruleIds(findings, Severity.ERROR),
                ruleIds(findings, Severity.WARNING),
                orUnknown(input),
                external,
                null,
                null,
                Instant.now().toString(),
                null);
    }

    /** Folder-batch desktop run; counts/score/elapsed come pre-aggregated. */
    public static UsageEvent fromBatch(BatchSummary summary,
                                       TemplateDefinition template,
                                       TemplateVersion version,
                                       AppSettings settings,
                                       long durationMs,
                                       Input input,
                                       External external) {
        int rows = 0;
        TreeSet<String> rules = new TreeSet<>();
        TreeSet<String> errorRules = new TreeSet<>();
        TreeSet<String> warningRules = new TreeSet<>();
        for (BatchResult r : summary.results()) {
            if (r.report() != null) {
                rows += r.report().file().rows().size();
            }
            for (Finding f : r.findings()) {
                if (f.ruleId() == null || f.ruleId().isBlank()) continue;
                rules.add(f.ruleId());
                if (f.severity() == Severity.ERROR) errorRules.add(f.ruleId());
                else if (f.severity() == Severity.WARNING) warningRules.add(f.ruleId());
            }
        }
        Double score = summary.aggregateOverallScore().isPresent()
                ? round2(summary.aggregateOverallScore().getAsDouble() * 100.0)
                : null;
        return new UsageEvent(
                TYPE_VALIDATE,
                STATUS_OK,
                settings.usageStats().installId(),
                "desktop",
                detectAppVersion(),
                osFamily(),
                detectJavaMajor(),
                template.id().name(),
                version.version(),
                profileCodes(summary.activeProfiles()),
                "batch",
                summary.results().size(),
                rows,
                (int) summary.aggregateErrors(),
                (int) summary.aggregateWarnings(),
                (int) summary.aggregateInfos(),
                score,
                clampMs(durationMs),
                settings.external().enabled(),
                List.copyOf(rules),
                List.copyOf(errorRules),
                List.copyOf(warningRules),
                orUnknown(input),
                external,
                null,
                null,
                Instant.now().toString(),
                null);
    }

    /**
     * Web run: no {@link AppSettings} exists server-side, so the web sentinel
     * install id is used and {@code externalEnabled} is passed explicitly.
     * {@code osName} is left null on purpose — the server's own OS is
     * meaningless here; the web layer fills it from the browser's User-Agent.
     */
    public static UsageEvent forWeb(QualityReport report,
                                    TemplateDefinition template,
                                    TemplateVersion version,
                                    boolean externalEnabled,
                                    long durationMs,
                                    Input input,
                                    External external,
                                    boolean isSample) {
        List<Finding> findings = report.findings();
        return new UsageEvent(
                TYPE_VALIDATE,
                STATUS_OK,
                WEB_INSTALL_ID,
                "web",
                detectAppVersion(),
                null,
                null,
                template.id().name(),
                version.version(),
                profileCodes(report.activeProfiles()),
                "single",
                1,
                report.file().rows().size(),
                countSeverity(findings, Severity.ERROR),
                countSeverity(findings, Severity.WARNING),
                countSeverity(findings, Severity.INFO),
                overallPercent(report.scores().get(ScoreCategory.OVERALL)),
                clampMs(durationMs),
                externalEnabled,
                ruleIds(findings),
                ruleIds(findings, Severity.ERROR),
                ruleIds(findings, Severity.WARNING),
                orUnknown(input),
                external,
                isSample,
                null,
                Instant.now().toString(),
                null);
    }

    /**
     * Desktop run that did not produce a report (load/parse failure, unexpected
     * error). Only the failure class is recorded — never the exception text.
     */
    public static UsageEvent failed(TemplateDefinition template,
                                    TemplateVersion version,
                                    AppSettings settings,
                                    String mode,
                                    String status,
                                    Input input) {
        return failed(template, version, settings, mode, status, input, null);
    }

    /**
     * As {@link #failed(TemplateDefinition, TemplateVersion, AppSettings, String, String, Input)},
     * plus a fail-reason code (typically {@link #failReasonOf(Throwable)}); it is
     * sanitised and capped by {@link #sanitizeFailReason(String)}.
     */
    public static UsageEvent failed(TemplateDefinition template,
                                    TemplateVersion version,
                                    AppSettings settings,
                                    String mode,
                                    String status,
                                    Input input,
                                    String failReason) {
        return new UsageEvent(
                TYPE_VALIDATE,
                status,
                settings.usageStats().installId(),
                "desktop",
                detectAppVersion(),
                osFamily(),
                detectJavaMajor(),
                template == null ? null : template.id().name(),
                version == null ? null : version.version(),
                List.of(),
                mode,
                1,
                null, null, null, null, null, null,
                settings.external().enabled(),
                List.of(),
                List.of(),
                List.of(),
                orUnknown(input),
                null,
                null,
                null,
                Instant.now().toString(),
                sanitizeFailReason(failReason));
    }

    /** Desktop report export (per-file, single xlsx, combined …). */
    public static UsageEvent export(TemplateDefinition template,
                                    TemplateVersion version,
                                    AppSettings settings,
                                    String mode,
                                    String exportKind,
                                    int fileCount) {
        return new UsageEvent(
                TYPE_REPORT_DOWNLOAD,
                STATUS_OK,
                settings.usageStats().installId(),
                "desktop",
                detectAppVersion(),
                osFamily(),
                detectJavaMajor(),
                template.id().name(),
                version.version(),
                List.of(),
                mode,
                Math.max(fileCount, 0),
                null, null, null, null, null, null,
                null,
                List.of(),
                List.of(),
                List.of(),
                Input.UNKNOWN,
                null,
                null,
                exportKind,
                Instant.now().toString(),
                null);
    }

    /**
     * Desktop app start: sent once per launch so installations that open the
     * app without validating anything still count as active. Carries only the
     * install id and the coarse runtime classes (version, OS family, Java).
     */
    public static UsageEvent appStart(AppSettings settings) {
        return presence(TYPE_APP_START, settings);
    }

    /** Desktop heartbeat: the app has been running for another 24 h. Same shape as {@link #appStart}. */
    public static UsageEvent heartbeat(AppSettings settings) {
        return presence(TYPE_HEARTBEAT, settings);
    }

    private static UsageEvent presence(String type, AppSettings settings) {
        return new UsageEvent(
                type,
                STATUS_OK,
                settings.usageStats().installId(),
                "desktop",
                detectAppVersion(),
                osFamily(),
                detectJavaMajor(),
                null,
                null,
                List.of(),
                null,
                0,
                null, null, null, null, null, null,
                null,
                List.of(),
                List.of(),
                List.of(),
                Input.UNKNOWN,
                null,
                null,
                null,
                Instant.now().toString(),
                null);
    }

    /**
     * Fail-reason code for an exception: the header statistics of a
     * {@link TemplateMismatchException} ({@code header_mismatch:<recognised>/<headers>}),
     * otherwise the exception's simple class name, followed by {@code /<root cause>}
     * when the innermost cause is of another class (e.g.
     * {@code IOException/ZipException}). Never the message.
     */
    public static String failReasonOf(Throwable th) {
        if (th == null) return null;
        if (th instanceof TemplateMismatchException tme && tme.match() != null) {
            return sanitizeFailReason(tme.match().failReason());
        }
        Throwable root = th;
        for (int depth = 0; root.getCause() != null && root.getCause() != root && depth < 20; depth++) {
            root = root.getCause();
        }
        String name = simpleName(th);
        if (root.getClass() != th.getClass()) name += "/" + simpleName(root);
        return sanitizeFailReason(name);
    }

    private static String simpleName(Throwable t) {
        String name = t.getClass().getSimpleName();
        if (name.isEmpty()) { // anonymous / hidden class
            name = t.getClass().getName();
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        return name;
    }

    /**
     * Normalises a fail-reason code: only {@code [A-Za-z0-9_.:/-]} survive
     * (anything else becomes {@code _}), at most {@value #MAX_FAIL_REASON}
     * chars; blank ⇒ {@code null}. Keeps free text (and with it any instance
     * data) out of the column even if a client sends one.
     */
    public static String sanitizeFailReason(String raw) {
        if (raw == null) return null;
        String t = raw.strip();
        if (t.isEmpty()) return null;
        if (t.length() > MAX_FAIL_REASON) t = t.substring(0, MAX_FAIL_REASON);
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == '_' || ch == '.' || ch == ':' || ch == '/' || ch == '-';
            sb.append(ok ? ch : '_');
        }
        return sb.toString();
    }

    private static Input orUnknown(Input input) {
        return input == null ? Input.UNKNOWN : input;
    }

    private static List<String> profileCodes(Set<ProfileKey> profiles) {
        Set<String> codes = new LinkedHashSet<>();
        if (profiles != null) {
            for (ProfileKey p : profiles) codes.add(p.code());
        }
        return List.copyOf(codes);
    }

    /** Distinct triggered rule ids, any severity (the {@code rule_ids} column, kept for compatibility). */
    private static List<String> ruleIds(List<Finding> findings) {
        return ruleIds(findings, null);
    }

    /**
     * Distinct rule ids of the findings with the given {@link Finding#severity()};
     * {@code null} = any severity. A rule that yields both errors and warnings
     * appears in both lists.
     */
    static List<String> ruleIds(List<Finding> findings, Severity severity) {
        TreeSet<String> ids = new TreeSet<>();
        for (Finding f : findings) {
            if (severity != null && f.severity() != severity) continue;
            if (f.ruleId() != null && !f.ruleId().isBlank()) ids.add(f.ruleId());
        }
        return List.copyOf(ids);
    }

    private static int countSeverity(List<Finding> findings, Severity s) {
        int n = 0;
        for (Finding f : findings) if (f.severity() == s) n++;
        return n;
    }

    private static Double overallPercent(Double fraction) {
        return fraction == null ? null : round2(fraction * 100.0);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Integer clampMs(long ms) {
        if (ms < 0) return 0;
        return ms > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ms;
    }

    /** Coarse OS family of the *running JVM* — desktop only, never the exact version. */
    static String osFamily() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "Windows";
        if (os.contains("mac") || os.contains("darwin")) return "Mac";
        if (os.contains("nux") || os.contains("nix") || os.contains("aix")) return "Linux";
        return "Other";
    }

    /** Java feature version of the running JVM (21, 24 …) — desktop only. */
    static Integer detectJavaMajor() {
        try {
            return Runtime.version().feature();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Resolves the running app version: {@link AppInfo#version()} (Maven-filtered
     * {@code META-INF/findatex-validator.properties}, works in the shaded desktop
     * jar and the Quarkus fast-jar alike) first, then the jar manifest's
     * {@code Implementation-Version}, else {@code "dev"}.
     */
    static String detectAppVersion() {
        String v = AppInfo.version();
        if (v != null && !v.isBlank() && !"dev".equals(v)) return v;
        v = UsageEvent.class.getPackage().getImplementationVersion();
        return v == null || v.isBlank() ? "dev" : v;
    }
}
