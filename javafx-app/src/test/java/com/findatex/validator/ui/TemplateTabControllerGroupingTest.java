package com.findatex.validator.ui;

import com.findatex.validator.validation.Finding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the by-error grouping used by the optional "Gruppieren nach Fehler" toggle:
 * one row per (severity, ruleId, fieldNum) bucket with the bucket size in the count column.
 */
class TemplateTabControllerGroupingTest {

    @Test
    void identicalSeverityRuleFieldCollapseIntoOneBucket() {
        List<Finding> findings = new ArrayList<>();
        for (int row = 1; row <= 88; row++) {
            findings.add(Finding.error("PRESENCE/5", null, "123", "TotalNetAssets",
                    row, null, "row " + row + " missing"));
        }
        findings.add(Finding.warn("XF-12", null, "40", "CouponFrequency", 1, null, "unusual"));

        List<TemplateTabController.FindingRow> groups =
                TemplateTabController.buildGroupRows(findings);

        assertThat(groups).hasSize(2);
        TemplateTabController.FindingRow presence = groups.get(0);
        assertThat(presence.getSeverity()).isEqualTo("ERROR");
        assertThat(presence.getRule()).isEqualTo("PRESENCE/5");
        assertThat(presence.getField()).isEqualTo("123");
        assertThat(presence.getCount()).isEqualTo("88");
        // Per-row context columns are intentionally blank in a group row.
        assertThat(presence.getRowIndex()).isEmpty();
        assertThat(presence.getFundId()).isEmpty();
    }

    @Test
    void differingFieldNumProducesDistinctBuckets() {
        List<Finding> findings = List.of(
                Finding.error("PRESENCE/5", null, "123", "A", 1, null, "missing"),
                Finding.error("PRESENCE/5", null, "456", "B", 1, null, "missing"));

        List<TemplateTabController.FindingRow> groups =
                TemplateTabController.buildGroupRows(findings);

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(TemplateTabController.FindingRow::getField)
                .containsExactlyInAnyOrder("123", "456");
    }

    @Test
    void groupsAreErrorsFirstThenByCountDesc() {
        List<Finding> findings = new ArrayList<>();
        // 2x WARNING — would beat any 1x ERROR by count alone, but severity wins first.
        findings.add(Finding.warn("W", null, "1", "f", 1, null, "x"));
        findings.add(Finding.warn("W", null, "1", "f", 2, null, "x"));
        findings.add(Finding.error("E", null, "1", "f", 3, null, "y"));

        List<TemplateTabController.FindingRow> groups =
                TemplateTabController.buildGroupRows(findings);

        assertThat(groups.get(0).getSeverity()).isEqualTo("ERROR");
        assertThat(groups.get(1).getSeverity()).isEqualTo("WARNING");
    }

    @Test
    void nullFieldNumBucketsCleanly() {
        List<Finding> findings = List.of(
                Finding.error("XF-1", null, null, null, 1, null, "cross-field A"),
                Finding.error("XF-1", null, null, null, 2, null, "cross-field B"));

        List<TemplateTabController.FindingRow> groups =
                TemplateTabController.buildGroupRows(findings);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getCount()).isEqualTo("2");
        assertThat(groups.get(0).getField()).isEmpty();
    }
}
