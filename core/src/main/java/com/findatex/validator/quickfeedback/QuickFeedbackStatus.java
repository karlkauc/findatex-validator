package com.findatex.validator.quickfeedback;

/**
 * Outcome of a quick-feedback (star rating) submission. Single source of truth
 * shared by the web layer (REST wire status), the JavaFX desktop client, and
 * the React frontend (which mirrors the lowercase wire names).
 *
 * <p>The wire/JSON representation is {@link #wire()} — the lowercase enum name
 * (e.g. {@code RATE_LIMITED} → {@code "rate_limited"}).
 */
public enum QuickFeedbackStatus {

    /** Feedback accepted (persistence is asynchronous and best-effort). */
    OK,
    /** Rating out of range or comment over the length limit. */
    INVALID,
    /** The per-IP submission budget is exhausted — try again later. */
    RATE_LIMITED,
    /** Feature not configured on the server, unreachable, or unexpected error. */
    UNAVAILABLE;

    /** Lowercase JSON/wire token, e.g. {@code "rate_limited"}. */
    public String wire() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Parses a wire token back to a status, defaulting to {@link #UNAVAILABLE}
     * for {@code null}/unknown input (never throws — callers treat anything
     * unrecognised as a soft failure).
     */
    public static QuickFeedbackStatus fromWire(String wire) {
        if (wire == null) return UNAVAILABLE;
        try {
            return valueOf(wire.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNAVAILABLE;
        }
    }
}
