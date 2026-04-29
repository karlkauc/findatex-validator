package com.findatex.validator.batch;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.template.tpt.TptTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BatchSummaryAggregationTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();
    private static final Set<ProfileKey> PROFILES = Set.of(
            TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB,
            TptProfiles.NW_675, TptProfiles.SST);

    @Test
    void meanIgnoresLoadErrors(@TempDir Path folder) throws Exception {
        Path bad = folder.resolve("broken.xlsx");
        Files.writeString(bad, "garbage");
        copySample("/sample/clean_v7.xlsx", folder.resolve("clean.xlsx"));

        BatchSummary summary = newService().run(
                List.of(bad, folder.resolve("clean.xlsx")), () -> false);

        long okCount = summary.countWithStatus(BatchFileStatus.OK);
        assertThat(okCount).isEqualTo(1);
        assertThat(summary.aggregateOverallScore()).isPresent();
        // Mean over a single OK report = that report's OVERALL score.
        double meanFromSummary = summary.aggregateOverallScore().getAsDouble();
        double expected = summary.results().stream()
                .filter(r -> r.status() == BatchFileStatus.OK)
                .findFirst().orElseThrow()
                .report().scores().get(com.findatex.validator.report.ScoreCategory.OVERALL);
        assertThat(meanFromSummary).isEqualTo(expected);
    }

    @Test
    void noOkResultsYieldsEmptyMean(@TempDir Path folder) throws Exception {
        Path bad = folder.resolve("broken.xlsx");
        Files.writeString(bad, "garbage");

        BatchSummary summary = newService().run(List.of(bad), () -> false);

        assertThat(summary.aggregateOverallScore()).isEmpty();
    }

    @Test
    void emptyResultListProducesEmptySummary() {
        BatchSummary summary = BatchSummary.of(
                List.of(),
                new TptTemplate().profilesFor(TptTemplate.V7_0),
                TptTemplate.V7_0,
                PROFILES,
                Instant.now(),
                Duration.ZERO,
                false);

        assertThat(summary.results()).isEmpty();
        assertThat(summary.aggregateErrors()).isZero();
        assertThat(summary.aggregateWarnings()).isZero();
        assertThat(summary.aggregateInfos()).isZero();
        assertThat(summary.aggregateOverallScore()).isEmpty();
    }

    @Test
    void severityCountsMatchSumOfPerFileFindings(@TempDir Path folder) throws Exception {
        copySample("/sample/bad_formats.xlsx", folder.resolve("bad.xlsx"));
        copySample("/sample/clean_v7.xlsx", folder.resolve("clean.xlsx"));

        BatchSummary summary = newService().run(
                List.of(folder.resolve("bad.xlsx"), folder.resolve("clean.xlsx")),
                () -> false);

        long e = summary.results().stream()
                .flatMap(r -> r.findings().stream())
                .filter(f -> f.severity() == com.findatex.validator.validation.Severity.ERROR)
                .count();
        long w = summary.results().stream()
                .flatMap(r -> r.findings().stream())
                .filter(f -> f.severity() == com.findatex.validator.validation.Severity.WARNING)
                .count();
        long i = summary.results().stream()
                .flatMap(r -> r.findings().stream())
                .filter(f -> f.severity() == com.findatex.validator.validation.Severity.INFO)
                .count();

        assertThat(summary.aggregateErrors()).isEqualTo(e);
        assertThat(summary.aggregateWarnings()).isEqualTo(w);
        assertThat(summary.aggregateInfos()).isEqualTo(i);
        assertThat(summary.aggregateBySeverity(com.findatex.validator.validation.Severity.ERROR))
                .isEqualTo(e);
    }

    private BatchValidationService newService() {
        BatchValidationOptions opts = new BatchValidationOptions(
                new TptTemplate(),
                TptTemplate.V7_0,
                PROFILES,
                false,
                AppSettings.defaults(),
                null);
        return new BatchValidationService(CATALOG, opts);
    }

    private static void copySample(String resource, Path target) throws Exception {
        URL url = BatchSummaryAggregationTest.class.getResource(resource);
        assertThat(url).as("missing test resource %s", resource).isNotNull();
        Files.copy(Path.of(url.toURI()), target, StandardCopyOption.REPLACE_EXISTING);
    }
}
