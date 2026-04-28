package com.findatex.validator.external;

import java.util.List;

/**
 * Per-template description of the columns that {@link ExternalValidationService} should
 * inspect for ISIN/LEI candidates and the optional context columns it cross-checks against.
 *
 * <p>Field codes are template-specific {@code numKey} strings (e.g. "14", "00010", "20000").
 * For columns whose payload is identifier-polymorphic (the cell can hold an ISIN, LEI, CUSIP,
 * etc., disambiguated by a sibling type-of-code flag), pass the sibling column as
 * {@code typeKey} and the discriminator value as {@code expectedTypeFlag}. For dedicated
 * columns whose semantic is fixed by the spec (e.g. EET 00030 producer LEI, alphanumeric
 * with no type flag), leave {@code typeKey} and {@code expectedTypeFlag} empty — the
 * service will then treat every syntactically valid value as a candidate.</p>
 */
public record ExternalValidationConfig(
        List<IdentifierRef> isinFields,
        List<IdentifierRef> leiFields,
        String currencyKey,
        String cicKey,
        String issuerNameKey,
        String issuerCountryKey) {

    /**
     * Reference to one identifier-bearing column.
     *
     * @param codeKey         {@code numKey} of the column holding the identifier value (required)
     * @param typeKey         {@code numKey} of the sibling type-of-code column, or {@code ""}
     *                        if the column is single-purpose
     * @param expectedTypeFlag value the type column must equal for the row to be picked up,
     *                        or {@code ""} when {@code typeKey} is empty
     */
    public record IdentifierRef(String codeKey, String typeKey, String expectedTypeFlag) {
        public IdentifierRef {
            if (codeKey == null || codeKey.isBlank()) {
                throw new IllegalArgumentException("codeKey must not be blank");
            }
            if (typeKey == null) typeKey = "";
            if (expectedTypeFlag == null) expectedTypeFlag = "";
            if (typeKey.isEmpty() ^ expectedTypeFlag.isEmpty()) {
                throw new IllegalArgumentException(
                        "typeKey and expectedTypeFlag must both be set or both be empty");
            }
        }

        public boolean hasTypeFlag() { return !typeKey.isEmpty(); }
    }

    public ExternalValidationConfig {
        isinFields = isinFields == null ? List.of() : List.copyOf(isinFields);
        leiFields = leiFields == null ? List.of() : List.copyOf(leiFields);
        currencyKey = currencyKey == null ? "" : currencyKey;
        cicKey = cicKey == null ? "" : cicKey;
        issuerNameKey = issuerNameKey == null ? "" : issuerNameKey;
        issuerCountryKey = issuerCountryKey == null ? "" : issuerCountryKey;
    }

    public boolean isEmpty() {
        return isinFields.isEmpty() && leiFields.isEmpty();
    }

    public static ExternalValidationConfig empty() {
        return new ExternalValidationConfig(List.of(), List.of(), "", "", "", "");
    }
}
