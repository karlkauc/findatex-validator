package com.findatex.validator.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                "installId", "source", "appVersion", "osName", "templateId",
                "templateVersion", "profiles", "mode", "fileCount", "rowCount",
                "errorCount", "warningCount", "infoCount", "overallScore",
                "durationMs", "externalEnabled", "ruleIds", "clientEventAt");
        // No field that could carry instance data.
        assertThat(names).doesNotContain("ip", "ipAddress", "fileName", "filename",
                "path", "isin", "lei", "fundName", "message", "value", "cells", "rows");
    }

    @Test
    void mapperEmitsOnlyAggregatesNeverSensitiveContent() throws Exception {
        QualityReport report = reportWithSecrets();
        UsageEvent ev = UsageEvent.from(report, TPT, V, AppSettings.defaults(),
                "single", 123L);

        assertThat(ev.source()).isEqualTo("desktop");
        assertThat(ev.mode()).isEqualTo("single");
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
                .doesNotContain("/test/");
    }

    @Test
    void webFactoryUsesSentinelInstallIdAndWebSource() {
        QualityReport report = reportWithSecrets();
        UsageEvent ev = UsageEvent.forWeb(report, TPT, V, true, 50L);
        assertThat(ev.installId()).isEqualTo(UsageEvent.WEB_INSTALL_ID);
        assertThat(ev.source()).isEqualTo("web");
        assertThat(ev.externalEnabled()).isTrue();
        assertThat(ev.mode()).isEqualTo("single");
    }

    @Test
    void withSourceWebSwapsToSentinel() {
        QualityReport report = reportWithSecrets();
        UsageEvent ev = UsageEvent.from(report, TPT, V, AppSettings.defaults(),
                "single", 1L).withSource("web");
        assertThat(ev.installId()).isEqualTo(UsageEvent.WEB_INSTALL_ID);
        assertThat(ev.source()).isEqualTo("web");
    }
}
