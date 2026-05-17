package com.findatex.validator.newsletter;

/**
 * Outcome of a newsletter sign-up attempt. Single source of truth shared by the
 * web layer (provider mapping + REST wire status), the JavaFX desktop client,
 * and the React frontend (which mirrors the lowercase wire names).
 *
 * <p>The wire/JSON representation is {@link #wire()} — the lowercase enum name
 * (e.g. {@code PENDING} → {@code "pending"}).
 */
public enum NewsletterStatus {

    /** Address accepted; provider sent a double-opt-in confirmation mail. */
    PENDING,
    /** Address is already an active (confirmed) subscriber. */
    SUBSCRIBED,
    /** Address was already subscribed and confirmed before this attempt. */
    ALREADY_SUBSCRIBED,
    /** Address was already pending confirmation before this attempt. */
    ALREADY_PENDING,
    /** The submitted address is syntactically invalid / rejected. */
    INVALID_EMAIL,
    /** Provider not configured, unreachable, or returned an unexpected error. */
    UNAVAILABLE;

    /** Lowercase JSON/wire token, e.g. {@code "already_pending"}. */
    public String wire() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Parses a wire token back to a status, defaulting to {@link #UNAVAILABLE}
     * for {@code null}/unknown input (never throws — callers treat anything
     * unrecognised as a soft failure).
     */
    public static NewsletterStatus fromWire(String wire) {
        if (wire == null) return UNAVAILABLE;
        try {
            return valueOf(wire.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNAVAILABLE;
        }
    }
}
