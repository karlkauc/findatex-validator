package com.findatex.validator.web.dto;

import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.FindingContext;

public record FindingDto(
        String severity,
        String ruleId,
        String profileCode,
        String fieldNum,
        String fieldName,
        Integer rowIndex,
        String value,
        String message,
        String portfolioId,
        String portfolioName,
        String valuationDate,
        String instrumentCode,
        String instrumentName,
        String valuationWeight
) {

    public static FindingDto from(Finding f) {
        FindingContext ctx = f.context();
        return new FindingDto(
                f.severity().name(),
                f.ruleId(),
                f.profile() == null ? null : f.profile().code(),
                f.fieldNum(),
                f.fieldName(),
                f.rowIndex(),
                f.value(),
                f.message(),
                ctx == null ? null : ctx.portfolioId(),
                ctx == null ? null : ctx.portfolioName(),
                ctx == null ? null : ctx.valuationDate(),
                ctx == null ? null : ctx.instrumentCode(),
                ctx == null ? null : ctx.instrumentName(),
                ctx == null ? null : ctx.valuationWeight()
        );
    }
}
