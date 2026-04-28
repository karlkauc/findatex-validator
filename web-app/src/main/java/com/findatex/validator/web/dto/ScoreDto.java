package com.findatex.validator.web.dto;

public record ScoreDto(
        String dimension,
        double value,
        Integer percentage
) {

    public static ScoreDto of(String dimension, double value) {
        int pct = (int) Math.round(value * 100.0);
        return new ScoreDto(dimension, value, pct);
    }
}
