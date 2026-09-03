package com.findatex.validator.web.dto;

import java.util.List;
import java.util.Map;

public record ValidationResponse(
        Summary summary,
        List<ScoreDto> scores,
        Map<String, List<ScoreDto>> perProfileScores,
        List<PerFundScoreDto> perFundScores,
        List<FindingDto> findings,
        String reportId,
        /**
         * Whether {@code GET /api/annotated-source/{reportId}} will serve the in-browser
         * annotated view for this run. False when the upload exceeded the configured size
         * cap or the side artifact could not be written — the XLSX report is unaffected.
         * Appended last so older clients simply ignore it.
         */
        boolean annotatedSourceAvailable
) {

    public record Summary(
            String templateId,
            String templateVersion,
            String filename,
            int rowCount,
            /**
             * Rows with no ERROR — the plain-language counterpart to the score,
             * which is a per-cell rate and therefore reads high on large files.
             * See QualityReport.cleanRowCount().
             */
            int cleanRowCount,
            int findingCount,
            int errorCount,
            int warningCount,
            int infoCount,
            String generatedAt
    ) {
    }
}
