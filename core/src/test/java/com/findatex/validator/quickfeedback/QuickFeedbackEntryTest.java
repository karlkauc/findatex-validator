package com.findatex.validator.quickfeedback;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuickFeedbackEntryTest {

    @Test
    void ratingBounds() {
        assertThat(QuickFeedbackEntry.isValidRating(null)).isFalse();
        assertThat(QuickFeedbackEntry.isValidRating(0)).isFalse();
        assertThat(QuickFeedbackEntry.isValidRating(1)).isTrue();
        assertThat(QuickFeedbackEntry.isValidRating(5)).isTrue();
        assertThat(QuickFeedbackEntry.isValidRating(6)).isFalse();
    }

    @Test
    void commentLengthLimitAppliesToTrimmedText() {
        assertThat(QuickFeedbackEntry.isValidComment(null)).isTrue();
        assertThat(QuickFeedbackEntry.isValidComment("")).isTrue();
        assertThat(QuickFeedbackEntry.isValidComment("x".repeat(2000))).isTrue();
        assertThat(QuickFeedbackEntry.isValidComment("x".repeat(2001))).isFalse();
        assertThat(QuickFeedbackEntry.isValidComment("  " + "x".repeat(2000) + "  ")).isTrue();
    }

    @Test
    void normaliseCommentTrimsAndNullsBlank() {
        assertThat(QuickFeedbackEntry.normaliseComment(null)).isNull();
        assertThat(QuickFeedbackEntry.normaliseComment("   ")).isNull();
        assertThat(QuickFeedbackEntry.normaliseComment("  hi  ")).isEqualTo("hi");
    }

    @Test
    void statusWireRoundTrip() {
        assertThat(QuickFeedbackStatus.RATE_LIMITED.wire()).isEqualTo("rate_limited");
        assertThat(QuickFeedbackStatus.fromWire("ok")).isEqualTo(QuickFeedbackStatus.OK);
        assertThat(QuickFeedbackStatus.fromWire("rate_limited")).isEqualTo(QuickFeedbackStatus.RATE_LIMITED);
        assertThat(QuickFeedbackStatus.fromWire(null)).isEqualTo(QuickFeedbackStatus.UNAVAILABLE);
        assertThat(QuickFeedbackStatus.fromWire("bogus")).isEqualTo(QuickFeedbackStatus.UNAVAILABLE);
    }
}
