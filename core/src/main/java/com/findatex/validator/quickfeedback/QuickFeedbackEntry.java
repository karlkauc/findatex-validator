package com.findatex.validator.quickfeedback;

/**
 * Shared validation rules for a quick-feedback submission — used by the desktop
 * {@link QuickFeedbackClient} (pre-validate before any network I/O) and by the
 * web resource (cheap reject before any work). Over-length comments are
 * rejected, never silently truncated.
 */
public final class QuickFeedbackEntry {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_COMMENT_LENGTH = 2000;

    private QuickFeedbackEntry() {}

    public static boolean isValidRating(Integer rating) {
        return rating != null && rating >= MIN_RATING && rating <= MAX_RATING;
    }

    public static boolean isValidComment(String comment) {
        return comment == null || comment.trim().length() <= MAX_COMMENT_LENGTH;
    }

    /** Trims the comment; blank or {@code null} becomes {@code null}. */
    public static String normaliseComment(String comment) {
        if (comment == null) return null;
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
