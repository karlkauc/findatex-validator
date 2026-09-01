package com.findatex.validator.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScorePercentTest {

    @Test
    void onlyAFlawlessScoreReportsHundred() {
        assertThat(ScorePercent.of(1.0)).isEqualTo(100);
        // The case this class exists for: the TPT showcase scores 0.9960 with
        // ten distinct rule families firing, and used to display as 100.
        assertThat(ScorePercent.of(0.9960454216966588)).isEqualTo(99);
        assertThat(ScorePercent.of(0.99999)).isEqualTo(99);
        assertThat(ScorePercent.of(0.995)).isEqualTo(99);
    }

    @Test
    void onlyAZeroScoreReportsZero() {
        assertThat(ScorePercent.of(0.0)).isZero();
        assertThat(ScorePercent.of(0.0001)).isEqualTo(1);
        assertThat(ScorePercent.of(0.004)).isEqualTo(1);
    }

    @Test
    void theMiddleOfTheScaleRoundsNormally() {
        assertThat(ScorePercent.of(0.5)).isEqualTo(50);
        assertThat(ScorePercent.of(0.874)).isEqualTo(87);
        assertThat(ScorePercent.of(0.876)).isEqualTo(88);
        assertThat(ScorePercent.of(0.005)).isEqualTo(1);
    }

    @Test
    void outOfRangeAndNaNAreHandledRatherThanPropagated() {
        assertThat(ScorePercent.of(1.5)).isEqualTo(100);
        assertThat(ScorePercent.of(-0.2)).isZero();
        assertThat(ScorePercent.of(Double.NaN)).isZero();
    }

    @Test
    void formatAppendsThePercentSign() {
        assertThat(ScorePercent.format(0.9960454216966588)).isEqualTo("99%");
        assertThat(ScorePercent.format(1.0)).isEqualTo("100%");
    }
}
