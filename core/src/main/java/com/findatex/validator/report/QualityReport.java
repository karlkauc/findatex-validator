package com.findatex.validator.report;

import com.findatex.validator.domain.FundKey;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record QualityReport(
        TptFile file,
        Set<ProfileKey> activeProfiles,
        List<Finding> findings,
        Map<ScoreCategory, Double> scores,
        Map<ProfileKey, Map<ScoreCategory, Double>> perProfileScores,
        Map<FundKey, Map<ScoreCategory, Double>> perFundScores,
        Instant generatedAt) {

    public QualityReport(TptFile file,
                         Set<ProfileKey> activeProfiles,
                         List<Finding> findings,
                         Map<ScoreCategory, Double> scores,
                         Map<ProfileKey, Map<ScoreCategory, Double>> perProfileScores,
                         Instant generatedAt) {
        this(file, activeProfiles, findings, scores, perProfileScores, Map.of(), generatedAt);
    }

    /** Rows in the file — positions for TPT, share classes for the others. */
    public int rowCount() {
        return file.rows().size();
    }

    /**
     * Rows carrying no ERROR-severity finding.
     *
     * <p>The plain-language counterpart to the score. Every score dimension is
     * a per-<em>cell</em> error rate, which reads high on large files: a real
     * 2 590-row delivery missing 18 717 mandatory cells still scores 95 %,
     * because 95 % of its cells are fine. "0 of 2 590 rows clean" carries the
     * same fact without needing interpretation.
     *
     * <p>WARNINGs deliberately leave a row clean. TPT files raise
     * COND_PRESENCE warnings on nearly every row, so counting them would push
     * this number to zero everywhere and destroy its only useful property —
     * telling a mostly-fine file apart from a broken one.
     *
     * <p>Findings with no row index (file- or fund-level) cannot be charged to
     * a particular row and are ignored here; they still appear in the findings
     * list and in the score.
     */
    public int cleanRowCount() {
        Set<Integer> rowsWithErrors = new HashSet<>();
        for (Finding f : findings) {
            if (f.severity() == Severity.ERROR && f.rowIndex() != null) {
                rowsWithErrors.add(f.rowIndex());
            }
        }
        int clean = 0;
        for (TptRow row : file.rows()) {
            if (!rowsWithErrors.contains(row.rowIndex())) clean++;
        }
        return clean;
    }
}
