package com.findatex.validator.web.dto;

import java.util.List;
import java.util.Map;

public record ValidationResponse(
        Summary summary,
        List<ScoreDto> scores,
        Map<String, List<ScoreDto>> perProfileScores,
        List<FindingDto> findings,
        String reportId
) {

    public record Summary(
            String templateId,
            String templateVersion,
            String filename,
            int rowCount,
            int findingCount,
            int errorCount,
            int warningCount,
            int infoCount,
            String generatedAt
    ) {
    }
}
