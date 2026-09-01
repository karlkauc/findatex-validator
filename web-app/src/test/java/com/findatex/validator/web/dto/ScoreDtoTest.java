package com.findatex.validator.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The percentage the SPA prints in the big score badge.
 *
 * <p>The badge is the one number most visitors read, and "100" is understood as
 * "nothing wrong with this file". Plain {@code Math.round} broke that promise:
 * anything from 0.995 upwards became 100, so a file with real errors could be
 * presented as flawless.
 */
class ScoreDtoTest {

    @Test
    void onlyAFlawlessScoreIsAllowedToShowAsHundred() {
        assertThat(ScoreDto.of("OVERALL", 1.0).percentage()).isEqualTo(100);
        assertThat(ScoreDto.of("OVERALL", 0.9960454216966588).percentage())
                .as("0.996 is not a perfect file and must not read as one")
                .isLessThan(100);
        assertThat(ScoreDto.of("OVERALL", 0.99999).percentage()).isLessThan(100);
        assertThat(ScoreDto.of("OVERALL", 0.995).percentage()).isLessThan(100);
    }

    @Test
    void aZeroPercentageMeansNothingScored() {
        assertThat(ScoreDto.of("OVERALL", 0.0).percentage()).isZero();
        assertThat(ScoreDto.of("OVERALL", 0.0001).percentage())
                .as("a non-zero score must not read as a total failure")
                .isGreaterThan(0);
    }

    @Test
    void ordinaryValuesStillRoundNormally() {
        assertThat(ScoreDto.of("OVERALL", 0.5).percentage()).isEqualTo(50);
        assertThat(ScoreDto.of("OVERALL", 0.874).percentage()).isEqualTo(87);
        assertThat(ScoreDto.of("OVERALL", 0.876).percentage()).isEqualTo(88);
    }

    @Test
    void theUnroundedValueIsPreservedForCallersThatWantPrecision() {
        assertThat(ScoreDto.of("OVERALL", 0.9960454216966588).value())
                .isEqualTo(0.9960454216966588);
    }
}
