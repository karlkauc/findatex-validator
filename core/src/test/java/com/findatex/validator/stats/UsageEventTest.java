package com.findatex.validator.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.findatex.validator.AppInfo;
import com.findatex.validator.config.AppSettings;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.report.QualityReport;
import com.findatex.validator.report.QualityScorer;
import com.findatex.validator.report.ScoreCategory;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateId;
import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.TestFileBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class UsageEventTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();
    private static TemplateDefinition TPT;
    private static TemplateVersion V;

    @BeforeAll
    static void registry() {
        TemplateRegistry.init();
        TPT = TemplateRegistry.of(TemplateId.TPT);
        V = TPT.latest();
    }

    private QualityReport reportWithSecrets() {
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("12", "FR0000120271", "3", "SECRET FUND NAME"))
                .build();
        // A finding carrying a raw cell value + human message — exactly the
        // sensitive content that must never reach the usage event.
        List<Finding> findings = List.of(
                Finding.error("PRESENCE/5/SOLVENCY_II", TptProfiles.SOLVENCY_II, "5",
                        "5_NetAssetValuation", 1, "TOPSECRETVALUE",
                        "Confidential message about FR0000120271"));
        return new QualityScorer(CATALOG).score(file, Set.of(TptProfiles.SOLVENCY_II), findings);
    }

    @Test
    void recordComponentsAreExactlyTheAllowedAggregateSet() {
        Set<String> names = Arrays.stream(UsageEvent.class.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder(
                "eventType", "status", "installId", "source", "appVersion", "osName",
                "javaMajor", "templateId", "templateVersion", "profiles", "mode",
                "fileCount", "rowCount", "errorCount", "warningCount", "infoCount",
                "overallScore", "durationMs", "externalEnabled", "ruleIds",
                "errorRuleIds", "warningRuleIds", "input", "external", "isSample", "exportKind", "clientEventAt", "failReason");
        // No field that could carry instance data — checked recursively through
        // the nested Input / External records as well.
        Set<String> all = new java.util.HashSet<>();
        collectComponentNames(UsageEvent.class, all);
        assertThat(all).doesNotContain("ip", "ipAddress", "fileName", "filename", "name",
                "path", "isin", "lei", "fundName", "message", "value", "cells", "rows",
                "userAgent", "visitorHash");
    }

    private static void collectComponentNames(Class<?> type, Set<String> into) {
        for (RecordComponent c : type.getRecordComponents()) {
            into.add(c.getName());
            if (c.getType().isRecord()) collectComponentNames(c.getType(), into);
        }
    }

    @Test
    void mapperEmitsOnlyAggregatesNeverSensitiveContent() throws Exception {
        QualityReport report = reportWithSecrets();
        UsageEvent.Input input = FileNameShape.of("20260331_TPTV7_SECRETFUNDCODE_LU9999999999.xlsx", 4096L);
        UsageEvent ev = UsageEvent.from(report, TPT, V, AppSettings.defaults(),
                "single", 123L, input, new UsageEvent.External(3, 1, 40, 0));

        assertThat(ev.eventType()).isEqualTo(UsageEvent.TYPE_VALIDATE);
        assertThat(ev.status()).isEqualTo(UsageEvent.STATUS_OK);
        assertThat(ev.source()).isEqualTo("desktop");
        assertThat(ev.mode()).isEqualTo("single");
        assertThat(ev.javaMajor()).isEqualTo(Runtime.version().feature());
        assertThat(ev.input().format()).isEqualTo("xlsx");
        assertThat(ev.input().bytes()).isEqualTo(4096L);
        assertThat(ev.input().namePattern()).isEqualTo(FileNameShape.PATTERN_DATED_TEMPLATE);
        assertThat(ev.external().lookups()).isEqualTo(3);
        assertThat(ev.fileCount()).isEqualTo(1);
        assertThat(ev.rowCount()).isEqualTo(1);
        assertThat(ev.errorCount()).isEqualTo(1);
        assertThat(ev.profiles()).containsExactly(TptProfiles.SOLVENCY_II.code());
        assertThat(ev.ruleIds()).containsExactly("PRESENCE/5/SOLVENCY_II");
        assertThat(ev.durationMs()).isEqualTo(123);
        // Score is the 0..1 OVERALL scaled to a 0..100 percentage, 2 decimals.
        double expected = Math.round(report.scores().get(ScoreCategory.OVERALL) * 100.0 * 100.0) / 100.0;
        assertThat(ev.overallScore()).isEqualTo(expected);
        assertThat(ev.overallScore()).isBetween(0.0, 100.0);

        String json = new ObjectMapper().writeValueAsString(ev);
        assertThat(json)
                .doesNotContain("TOPSECRETVALUE")
                .doesNotContain("SECRET FUND NAME")
                .doesNotContain("FR0000120271")
                .doesNotContain("Confidential message")
                .doesNotContain("in-memory")
                .doesNotContain("/test/")
                .doesNotContain("SECRETFUNDCODE")
                .doesNotContain("LU9999999999")
                .doesNotContain("20260331");
    }

    @Test
    void webFactoryUsesSentinelInstallIdAndWebSource() {
        QualityReport report = reportWithSecrets();
        UsageEvent ev = UsageEvent.forWeb(report, TPT, V, true, 50L, null, null, true);
        assertThat(ev.installId()).isEqualTo(UsageEvent.WEB_INSTALL_ID);
        assertThat(ev.source()).isEqualTo("web");
        assertThat(ev.externalEnabled()).isTrue();
        assertThat(ev.mode()).isEqualTo("single");
        assertThat(ev.isSample()).isTrue();
        // The server's own OS / JVM must never be recorded for a browser run —
        // the web layer fills os_name from the User-Agent instead.
        assertThat(ev.osName()).isNull();
        assertThat(ev.javaMajor()).isNull();
        assertThat(ev.input()).isEqualTo(UsageEvent.Input.UNKNOWN);
    }

    @Test
    void failedRunCarriesOnlyTheStatusClass() {
        UsageEvent ev = UsageEvent.failed(TPT, V, AppSettings.defaults(), "single",
                UsageEvent.STATUS_PARSE_ERROR, FileNameShape.of("broken.csv", 12L));
        assertThat(ev.eventType()).isEqualTo(UsageEvent.TYPE_VALIDATE);
        assertThat(ev.status()).isEqualTo(UsageEvent.STATUS_PARSE_ERROR);
        assertThat(ev.templateId()).isEqualTo("TPT");
        assertThat(ev.rowCount()).isNull();
        assertThat(ev.overallScore()).isNull();
        assertThat(ev.input().format()).isEqualTo("csv");
        assertThat(ev.profiles()).isEmpty();
    }

    @Test
    void exportEventRecordsKindAndCount() throws Exception {
        UsageEvent ev = UsageEvent.export(TPT, V, AppSettings.defaults(), "batch",
                UsageEvent.EXPORT_PER_FILE, 7);
        assertThat(ev.eventType()).isEqualTo(UsageEvent.TYPE_REPORT_DOWNLOAD);
        assertThat(ev.exportKind()).isEqualTo("per_file");
        assertThat(ev.fileCount()).isEqualTo(7);
        assertThat(ev.status()).isEqualTo(UsageEvent.STATUS_OK);
        assertThat(new ObjectMapper().writeValueAsString(ev)).contains("\"report_download\"");
    }

    @Test
    void appVersionComesFromAppInfoNotJarManifest() {
        // The Quarkus fast-jar / plain core jar carry no Implementation-Version in
        // the manifest, so the Maven-filtered AppInfo.version() must win — otherwise
        // production web runs are recorded as "dev".
        assertThat(UsageEvent.detectAppVersion()).isEqualTo(AppInfo.version());
        assertThat(AppInfo.version()).isNotEqualTo("dev");
    }

    @Test
    void appStartAndHeartbeatCarryOnlyInstallAndRuntimeClasses() throws Exception {
        AppSettings settings = AppSettings.defaults();
        for (UsageEvent ev : List.of(UsageEvent.appStart(settings), UsageEvent.heartbeat(settings))) {
            assertThat(ev.status()).isEqualTo(UsageEvent.STATUS_OK);
            assertThat(ev.source()).isEqualTo("desktop");
            assertThat(ev.installId()).isEqualTo(settings.usageStats().installId());
            assertThat(ev.appVersion()).isEqualTo(UsageEvent.detectAppVersion());
            assertThat(ev.osName()).isIn("Windows", "Mac", "Linux", "Other");
            assertThat(ev.javaMajor()).isEqualTo(Runtime.version().feature());
            assertThat(ev.clientEventAt()).isNotBlank();
            // Not a run: no template, no counts, no file attributes.
            assertThat(ev.templateId()).isNull();
            assertThat(ev.templateVersion()).isNull();
            assertThat(ev.mode()).isNull();
            assertThat(ev.fileCount()).isZero();
            assertThat(ev.rowCount()).isNull();
            assertThat(ev.errorCount()).isNull();
            assertThat(ev.overallScore()).isNull();
            assertThat(ev.durationMs()).isNull();
            assertThat(ev.externalEnabled()).isNull();
            assertThat(ev.profiles()).isEmpty();
            assertThat(ev.ruleIds()).isEmpty();
            assertThat(ev.input()).isEqualTo(UsageEvent.Input.UNKNOWN);
            assertThat(ev.external()).isNull();
            assertThat(ev.exportKind()).isNull();
        }
        assertThat(UsageEvent.appStart(settings).eventType()).isEqualTo("app_start");
        assertThat(UsageEvent.heartbeat(settings).eventType()).isEqualTo("heartbeat");
        assertThat(new ObjectMapper().writeValueAsString(UsageEvent.heartbeat(settings)))
                .contains("\"eventType\":\"heartbeat\"");
    }

    @Test
    void ruleIdsAreSplitBySeverity() {
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("12", "FR0000120271", "3", "FUND"))
                .build();
        List<Finding> findings = List.of(
                Finding.error("ERR-ONLY", TptProfiles.SOLVENCY_II, "5", "f", 1, "v", "m"),
                Finding.error("BOTH", TptProfiles.SOLVENCY_II, "5", "f", 1, "v", "m"),
                Finding.warn("BOTH", TptProfiles.SOLVENCY_II, "5", "f", 1, "v", "m"),
                Finding.warn("WARN-ONLY", TptProfiles.SOLVENCY_II, "5", "f", 1, "v", "m"),
                Finding.warn("WARN-ONLY", TptProfiles.SOLVENCY_II, "5", "f", 2, "v", "m"),
                Finding.info("INFO-ONLY", TptProfiles.SOLVENCY_II, "5", "f", 1, "v", "m"));
        QualityReport report = new QualityScorer(CATALOG).score(file, Set.of(TptProfiles.SOLVENCY_II), findings);

        UsageEvent ev = UsageEvent.from(report, TPT, V, AppSettings.defaults(), "single", 1L, null, null);
        // rule_ids stays the union of all severities (compatibility).
        assertThat(ev.ruleIds()).containsExactly("BOTH", "ERR-ONLY", "INFO-ONLY", "WARN-ONLY");
        assertThat(ev.errorRuleIds()).containsExactly("BOTH", "ERR-ONLY");
        assertThat(ev.warningRuleIds()).containsExactly("BOTH", "WARN-ONLY");

        UsageEvent web = UsageEvent.forWeb(report, TPT, V, false, 1L, null, null, false);
        assertThat(web.errorRuleIds()).isEqualTo(ev.errorRuleIds());
        assertThat(web.warningRuleIds()).isEqualTo(ev.warningRuleIds());

        // Events without findings carry empty lists, never null.
        UsageEvent failed = UsageEvent.failed(TPT, V, AppSettings.defaults(), "single",
                UsageEvent.STATUS_ERROR, null);
        assertThat(failed.errorRuleIds()).isEmpty();
        assertThat(failed.warningRuleIds()).isEmpty();
        assertThat(UsageEvent.appStart(AppSettings.defaults()).errorRuleIds()).isEmpty();
    }

    @Test
    void failReasonIsACodeNeverAMessage() throws Exception {
        // Template mismatch: header counts only, not the header texts in the message.
        com.findatex.validator.ingest.HeaderMatch match =
                new com.findatex.validator.ingest.HeaderMatch(57, 0, List.of("Secret header"));
        assertThat(UsageEvent.failReasonOf(new com.findatex.validator.ingest.TemplateMismatchException(
                "File does not match ... \"Secret header\"", match))).isEqualTo("header_mismatch:0/57");
        assertThat(match.failReason()).isEqualTo("header_mismatch:0/57");

        // Exceptions: simple class name (+ root cause class), never the message.
        assertThat(UsageEvent.failReasonOf(new java.io.IOException("C:\\funds\\SECRET.xlsx")))
                .isEqualTo("IOException");
        assertThat(UsageEvent.failReasonOf(new java.io.IOException("outer secret",
                new RuntimeException("mid", new java.util.zip.ZipException("inner secret")))))
                .isEqualTo("IOException/ZipException");
        assertThat(UsageEvent.failReasonOf(new com.findatex.validator.ingest.TemplateMismatchException("no match")))
                .isEqualTo("TemplateMismatchException");
        assertThat(UsageEvent.failReasonOf(null)).isNull();

        // Sanitiser: allowed chars only, capped, blank -> null.
        assertThat(UsageEvent.sanitizeFailReason("  ")).isNull();
        assertThat(UsageEvent.sanitizeFailReason("a b\"c")).isEqualTo("a_b_c");
        assertThat(UsageEvent.sanitizeFailReason("x".repeat(300))).hasSize(UsageEvent.MAX_FAIL_REASON);

        UsageEvent ev = UsageEvent.failed(TPT, V, AppSettings.defaults(), "single",
                UsageEvent.STATUS_PARSE_ERROR, null,
                UsageEvent.failReasonOf(new java.io.IOException("SECRET")));
        assertThat(ev.failReason()).isEqualTo("IOException");
        assertThat(new ObjectMapper().writeValueAsString(ev))
                .contains("\"failReason\":\"IOException\"").doesNotContain("SECRET");
        // Successful and non-run events carry none.
        assertThat(UsageEvent.failed(TPT, V, AppSettings.defaults(), "single",
                UsageEvent.STATUS_ERROR, null).failReason()).isNull();
        assertThat(UsageEvent.appStart(AppSettings.defaults()).failReason()).isNull();
    }
}
