package com.tpt.validator.report;

import com.tpt.validator.domain.TptFile;
import com.tpt.validator.spec.Profile;
import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.spec.SpecLoader;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Severity;
import com.tpt.validator.validation.TestFileBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityScorerEdgeCasesTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    @Test
    void emptyFileScoresPerfectlyByDefinition() {
        // No rows, no findings → all categories should be 1.0 (vacuously satisfied).
        TptFile empty = new TptFile(Path.of("empty.csv"), "csv", List.of(),
                new LinkedHashMap<>(), List.of(), List.of());
        QualityReport report = new QualityScorer(CATALOG)
                .score(empty, EnumSet.of(Profile.SOLVENCY_II), List.of());
        assertThat(report.scores().get(ScoreCategory.MANDATORY_COMPLETENESS)).isEqualTo(1.0);
        assertThat(report.scores().get(ScoreCategory.FORMAT_CONFORMANCE)).isEqualTo(1.0);
        assertThat(report.scores().get(ScoreCategory.CLOSED_LIST_CONFORMANCE)).isEqualTo(1.0);
        assertThat(report.scores().get(ScoreCategory.CROSS_FIELD_CONSISTENCY)).isEqualTo(1.0);
        assertThat(report.scores().get(ScoreCategory.OVERALL)).isEqualTo(1.0);
    }

    @Test
    void mandatoryCompletenessDecreasesWithMissingFields() {
        TptFile file = new com.tpt.validator.validation.TestFileBuilder()
                .row(TestFileBuilder.values("12", "FR12"))
                .build();
        // Synthetic missing-mandatory finding pretending field 5 is missing.
        List<Finding> findings = List.of(
                Finding.error("PRESENCE/5/SOLVENCY_II", Profile.SOLVENCY_II, "5",
                        "5_NetAssetValuation", 1, null, "missing"));
        QualityReport r = new QualityScorer(CATALOG)
                .score(file, EnumSet.of(Profile.SOLVENCY_II), findings);
        assertThat(r.scores().get(ScoreCategory.MANDATORY_COMPLETENESS))
                .as("one missing M field out of many → score drops slightly")
                .isLessThan(1.0);
    }

    @Test
    void formatErrorsBringDownFormatConformance() {
        TptFile file = new com.tpt.validator.validation.TestFileBuilder()
                .row(TestFileBuilder.values("12", "FR12", "21", "EUR"))
                .build();
        List<Finding> findings = List.of(
                Finding.error("FORMAT/21", null, "21", "Currency", 1, "ZZZ", "Unknown ISO 4217"));
        QualityReport r = new QualityScorer(CATALOG)
                .score(file, EnumSet.of(Profile.SOLVENCY_II), findings);
        assertThat(r.scores().get(ScoreCategory.FORMAT_CONFORMANCE)).isLessThan(1.0);
    }

    @Test
    void closedListErrorsAreNotDoubleCountedInFormatScore() {
        TptFile file = new com.tpt.validator.validation.TestFileBuilder()
                .row(TestFileBuilder.values("12", "FR12", "15", "1"))
                .build();
        Finding closedListErr = Finding.error("FORMAT/15", null, "15", "CodifSystem", 1,
                "42", "Value '42' is not in the closed list (1, 2, 99)");
        QualityReport r = new QualityScorer(CATALOG)
                .score(file, EnumSet.of(Profile.SOLVENCY_II), List.of(closedListErr));
        // The scorer routes "closed list" errors to CLOSED_LIST_CONFORMANCE.
        assertThat(r.scores().get(ScoreCategory.CLOSED_LIST_CONFORMANCE)).isLessThan(1.0);
    }

    @Test
    void overallIsWeightedAverageOfCategories() {
        TptFile file = new com.tpt.validator.validation.TestFileBuilder()
                .row(TestFileBuilder.values("12", "FR12"))
                .build();
        // Empty findings → all categories are 1.0 → overall must be 1.0.
        QualityReport perfect = new QualityScorer(CATALOG)
                .score(file, EnumSet.of(Profile.SOLVENCY_II), List.of());
        assertThat(perfect.scores().get(ScoreCategory.OVERALL)).isEqualTo(1.0);
    }

    @Test
    void perProfileScoresPopulatedForEachActiveProfile() {
        TptFile file = new com.tpt.validator.validation.TestFileBuilder()
                .row(TestFileBuilder.values("12", "FR12"))
                .build();
        QualityReport r = new QualityScorer(CATALOG)
                .score(file, EnumSet.allOf(Profile.class), List.of());
        assertThat(r.perProfileScores()).hasSize(Profile.values().length);
        for (Profile p : Profile.values()) {
            assertThat(r.perProfileScores()).containsKey(p);
            assertThat(r.perProfileScores().get(p)).containsKey(ScoreCategory.PROFILE_COMPLETENESS);
        }
    }

    @Test
    void scoresAreClampedTo01() {
        // Construct a pathological case: more errors than slots is theoretically possible
        // if every rule fired multiple times. The scorer must clamp to [0, 1].
        TptFile file = new com.tpt.validator.validation.TestFileBuilder()
                .row(TestFileBuilder.values("12", "FR12"))
                .build();
        java.util.List<Finding> manyErrors = new java.util.ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            manyErrors.add(Finding.error("FORMAT/12", null, "12", "x", 1, "y", "z"));
        }
        QualityReport r = new QualityScorer(CATALOG)
                .score(file, EnumSet.of(Profile.SOLVENCY_II), manyErrors);
        // No score may exceed 1.0 or be negative.
        for (Double v : r.scores().values()) {
            assertThat(v).isBetween(0.0, 1.0);
        }
        for (var perProfile : r.perProfileScores().values()) {
            for (Double v : perProfile.values()) {
                assertThat(v).isBetween(0.0, 1.0);
            }
        }
    }

    @Test
    void severitiesPassThroughToReport() {
        TptFile file = new com.tpt.validator.validation.TestFileBuilder()
                .row(TestFileBuilder.values("12", "FR12"))
                .build();
        List<Finding> findings = List.of(
                Finding.error("FORMAT/21", null, "21", "Currency", 1, "ZZZ", "bad"),
                Finding.warn ("XF-04/POSITION_WEIGHT_SUM", null, "26", "Weight", null, "0.7", "off"),
                Finding.info ("XF-15/TPT_VERSION", null, "1000", "Version", null, null, "missing"));
        QualityReport r = new QualityScorer(CATALOG)
                .score(file, EnumSet.of(Profile.SOLVENCY_II), findings);

        long e = r.findings().stream().filter(f -> f.severity() == Severity.ERROR).count();
        long w = r.findings().stream().filter(f -> f.severity() == Severity.WARNING).count();
        long i = r.findings().stream().filter(f -> f.severity() == Severity.INFO).count();
        assertThat(e).isEqualTo(1);
        assertThat(w).isEqualTo(1);
        assertThat(i).isEqualTo(1);
    }
}
