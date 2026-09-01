package com.findatex.validator.web.dto;

import com.findatex.validator.report.ScorePercent;

public record ScoreDto(
        String dimension,
        double value,
        Integer percentage
) {

    /**
     * @param value the score in [0, 1]; {@code percentage} is what the SPA
     *              prints in the score badge.
     *
     * <p>Rounding rules live in {@link ScorePercent} so the desktop batch list
     * and this badge cannot disagree about when a file counts as flawless.
     * {@link #value()} keeps the unrounded number for callers wanting precision.
     */
    public static ScoreDto of(String dimension, double value) {
        return new ScoreDto(dimension, value, ScorePercent.of(value));
    }
}
