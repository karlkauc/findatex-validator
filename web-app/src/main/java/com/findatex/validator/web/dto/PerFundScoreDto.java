package com.findatex.validator.web.dto;

import java.util.List;

public record PerFundScoreDto(
        String portfolioId,
        String portfolioName,
        String valuationDate,
        List<ScoreDto> scores
) {
}
