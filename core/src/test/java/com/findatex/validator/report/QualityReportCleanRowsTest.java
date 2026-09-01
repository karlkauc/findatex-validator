package com.findatex.validator.report;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.TestFileBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Rows with no error" — the plain-language counterpart to the score.
 *
 * <p>It exists because the score is a per-cell error rate and therefore reads
 * high on big files: a real 2 590-row delivery with 18 717 errors still scores
 * 95 %, because 95 % of its cells are fine. "0 of 2 590 rows clean" says the
 * same thing in a way nobody has to interpret. All three UIs read this one
 * method so they cannot drift apart.
 */
class QualityReportCleanRowsTest {

    private static final Set<ProfileKey> PROFILES = Set.of(TptProfiles.SOLVENCY_II);

    @Test
    void everyRowIsCleanWhenNothingWasFound() {
        QualityReport r = report(3, List.of());
        assertThat(r.cleanRowCount()).isEqualTo(3);
        assertThat(r.rowCount()).isEqualTo(3);
    }

    @Test
    void aRowWithAnErrorIsNotClean() {
        QualityReport r = report(3, List.of(error(2)));
        assertThat(r.cleanRowCount()).isEqualTo(2);
    }

    @Test
    void severalErrorsOnOneRowStillCostOnlyThatRow() {
        QualityReport r = report(3, List.of(error(2), error(2), error(2)));
        assertThat(r.cleanRowCount())
                .as("the count is per row, not per finding")
                .isEqualTo(2);
    }

    @Test
    void warningsLeaveARowClean() {
        // Deliberate: TPT files carry COND_PRESENCE warnings on nearly every
        // row, so counting them would make the number useless.
        QualityReport r = report(3, List.of(
                Finding.warn("COND_PRESENCE/42/SOLVENCY_II", null, "42", "Callable", 2, null, "missing"),
                Finding.info("PROFILE/X/SKIPPED", null, null, null, 3, null, "skipped")));
        assertThat(r.cleanRowCount()).isEqualTo(3);
    }

    @Test
    void fileLevelErrorsWithoutARowAreNotChargedToAnyRow() {
        QualityReport r = report(3, List.of(
                Finding.error("XF-15/TPT_VERSION", null, "1000", "Version", null, null, "wrong version")));
        assertThat(r.cleanRowCount())
                .as("a finding with no row cannot make a particular row dirty")
                .isEqualTo(3);
    }

    @Test
    void anErrorOnAnUnknownRowIndexIsIgnoredRatherThanDoubleCounted() {
        QualityReport r = report(3, List.of(error(99)));
        assertThat(r.cleanRowCount()).isEqualTo(3);
    }

    @Test
    void anEmptyFileHasNoCleanRowsAndNoRows() {
        QualityReport r = report(0, List.of());
        assertThat(r.rowCount()).isZero();
        assertThat(r.cleanRowCount()).isZero();
    }

    private static Finding error(int rowIndex) {
        return Finding.error("PRESENCE/5/SOLVENCY_II", TptProfiles.SOLVENCY_II, "5",
                "NAV", rowIndex, null, "missing");
    }

    private static QualityReport report(int rows, List<Finding> findings) {
        TestFileBuilder b = new TestFileBuilder();
        for (int i = 0; i < rows; i++) {
            b.row(TestFileBuilder.values("12", "FR11"));
        }
        TptFile file = b.build();
        return new QualityReport(file, PROFILES, findings, Map.of(), Map.of(), Instant.now());
    }
}
