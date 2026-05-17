package com.findatex.validator.stats;

import com.findatex.validator.batch.BatchResult;
import com.findatex.validator.batch.BatchSummary;
import com.findatex.validator.config.AppSettings;
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
 * Anonymous, aggregate-only record of one validation run. Carries deliberately
 * coarse counts and identifiers — never file names, paths, fund names,
 * ISIN/LEI/codes, cell values, or {@code Finding.message()/value()}. The
 * server derives {@code country_code} from the request IP; the raw IP is never
 * part of this event and never leaves the desktop.
 *
 * <p>{@code overallScore} is the OVERALL category scaled to a 0..100 percentage
 * with two decimals (the scorer works on a 0..1 fraction).
 */
public record UsageEvent(
        String installId,
        String source,
        String appVersion,
        String osName,
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
        String clientEventAt) {

    /** Sentinel install id used for runs that originate in the web layer. */
    public static final String WEB_INSTALL_ID = "00000000-0000-0000-0000-000000000000";

    /** Single-file desktop run. */
    public static UsageEvent from(QualityReport report,
                                  TemplateDefinition template,
                                  TemplateVersion version,
                                  AppSettings settings,
                                  String mode,
                                  long durationMs) {
        List<Finding> findings = report.findings();
        return new UsageEvent(
                settings.usageStats().installId(),
                "desktop",
                detectAppVersion(),
                osFamily(),
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
                Instant.now().toString());
    }

    /** Folder-batch desktop run; counts/score/elapsed come pre-aggregated. */
    public static UsageEvent fromBatch(BatchSummary summary,
                                       TemplateDefinition template,
                                       TemplateVersion version,
                                       AppSettings settings,
                                       long durationMs) {
        int rows = 0;
        TreeSet<String> rules = new TreeSet<>();
        for (BatchResult r : summary.results()) {
            if (r.report() != null) {
                rows += r.report().file().rows().size();
            }
            for (Finding f : r.findings()) {
                if (f.ruleId() != null && !f.ruleId().isBlank()) rules.add(f.ruleId());
            }
        }
        Double score = summary.aggregateOverallScore().isPresent()
                ? round2(summary.aggregateOverallScore().getAsDouble() * 100.0)
                : null;
        return new UsageEvent(
                settings.usageStats().installId(),
                "desktop",
                detectAppVersion(),
                osFamily(),
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
                Instant.now().toString());
    }

    /**
     * Web run: no {@link AppSettings} exists server-side, so the web sentinel
     * install id is used and {@code externalEnabled} is passed explicitly.
     */
    public static UsageEvent forWeb(QualityReport report,
                                    TemplateDefinition template,
                                    TemplateVersion version,
                                    boolean externalEnabled,
                                    long durationMs) {
        List<Finding> findings = report.findings();
        return new UsageEvent(
                WEB_INSTALL_ID,
                "web",
                detectAppVersion(),
                osFamily(),
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
                Instant.now().toString());
    }

    /** Returns a copy tagged for the web layer (web sentinel install id). */
    public UsageEvent withSource(String newSource) {
        boolean web = "web".equals(newSource);
        return new UsageEvent(
                web ? WEB_INSTALL_ID : installId,
                newSource, appVersion, osName, templateId, templateVersion,
                profiles, mode, fileCount, rowCount, errorCount, warningCount,
                infoCount, overallScore, durationMs, externalEnabled, ruleIds,
                clientEventAt);
    }

    private static List<String> profileCodes(Set<ProfileKey> profiles) {
        Set<String> codes = new LinkedHashSet<>();
        if (profiles != null) {
            for (ProfileKey p : profiles) codes.add(p.code());
        }
        return List.copyOf(codes);
    }

    private static List<String> ruleIds(List<Finding> findings) {
        TreeSet<String> ids = new TreeSet<>();
        for (Finding f : findings) {
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

    /** Coarse OS family only — never the exact version. */
    static String osFamily() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "Windows";
        if (os.contains("mac") || os.contains("darwin")) return "Mac";
        if (os.contains("nux") || os.contains("nix") || os.contains("aix")) return "Linux";
        return "Other";
    }

    static String detectAppVersion() {
        String v = UsageEvent.class.getPackage().getImplementationVersion();
        return (v == null || v.isBlank()) ? "dev" : v;
    }
}
