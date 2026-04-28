package com.findatex.validator.web.dto;

import java.util.Optional;

/**
 * Per-request user choices for the external (GLEIF / OpenFIGI) validation phase.
 *
 * <p>This is an internal carrier between {@code ValidationResource} and
 * {@code ValidationOrchestrator}. It is never serialised back to the client —
 * in particular {@link #userOpenfigiKey()} must never be logged or echoed.
 */
public record ExternalOptions(
        boolean enabled,
        boolean leiEnabled,
        boolean leiCheckLapsed,
        boolean leiCheckName,
        boolean leiCheckCountry,
        boolean isinEnabled,
        boolean isinCheckCurrency,
        boolean isinCheckCic,
        Optional<String> userOpenfigiKey
) {

    public static ExternalOptions disabled() {
        return new ExternalOptions(false, true, true, false, false,
                true, false, false, Optional.empty());
    }
}
