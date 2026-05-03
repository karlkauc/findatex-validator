package com.findatex.validator.ui;

import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: when a TPT file produces tens of thousands of findings, the 10K display cap used
 * to truncate by raw insertion order. If a rule emitted >10K WARNINGs before any presence-rule
 * ERROR fired, every error fell past the cap and the findings table looked empty even though
 * the file-row "Errors" column counted thousands. {@link TemplateTabController#prepareDisplayBatch}
 * sorts severity-first so errors always survive any truncation.
 */
class TemplateTabControllerDisplayCapTest {

    @Test
    void errorsSurviveCapEvenWhenWarningsDominateAndComeFirst() {
        List<Finding> findings = new ArrayList<>();
        // 20K warnings up front (matches the real reproduction: 30K+ warnings, 5K errors).
        for (int i = 0; i < 20_000; i++) {
            findings.add(Finding.warn("WARN/" + i, null, "1", "f", i, null, "w"));
        }
        // 500 errors at the tail — these would be lost under naive head-truncation.
        for (int i = 0; i < 500; i++) {
            findings.add(Finding.error("ERR/" + i, null, "1", "f", i, null, "e"));
        }

        TemplateTabController.DisplayBatch batch =
                TemplateTabController.prepareDisplayBatch(findings);

        assertThat(batch.rows()).hasSize(10_000);
        assertThat(batch.totalBeforeCap()).isEqualTo(20_500);
        long errorsKept = batch.rows().stream()
                .filter(f -> f.severity() == Severity.ERROR)
                .count();
        assertThat(errorsKept).isEqualTo(500);
    }

    @Test
    void noTruncationFlagWhenUnderTheCap() {
        List<Finding> findings = List.of(
                Finding.error("E", null, "1", "f", 1, null, "e"),
                Finding.warn("W", null, "1", "f", 1, null, "w"),
                Finding.info("I", null, "1", "f", 1, null, "i"));

        TemplateTabController.DisplayBatch batch =
                TemplateTabController.prepareDisplayBatch(findings);

        assertThat(batch.totalBeforeCap()).isZero();
        assertThat(batch.rows()).extracting(Finding::severity)
                .containsExactly(Severity.ERROR, Severity.WARNING, Severity.INFO);
    }
}
