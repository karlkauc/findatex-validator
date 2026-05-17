package com.findatex.validator.newsletter;

import java.util.regex.Pattern;

/**
 * Minimal, deliberately permissive e-mail syntax check shared by the desktop
 * client and the web layer. This is a sanity gate to avoid pointless outbound
 * calls and obvious typos — it is <b>not</b> RFC 5322 and not a deliverability
 * check. The newsletter provider performs the authoritative validation and the
 * double-opt-in mail is the real proof of a working address.
 */
public final class EmailAddress {

    private EmailAddress() {}

    /** One {@code @}, non-empty local part, a dotted domain, sane length. */
    private static final Pattern BASIC =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final int MAX_LENGTH = 254;

    /** {@code true} when {@code raw} is a plausible address (after trimming). */
    public static boolean isValid(String raw) {
        if (raw == null) return false;
        String e = raw.trim();
        return e.length() >= 3
                && e.length() <= MAX_LENGTH
                && BASIC.matcher(e).matches();
    }

    /** Trimmed address; {@code ""} for {@code null}. Does not validate. */
    public static String normalise(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
