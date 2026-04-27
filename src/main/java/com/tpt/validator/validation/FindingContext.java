package com.tpt.validator.validation;

/**
 * Optional context attached to a {@link Finding} during enrichment so the
 * report can answer "which fund?" and "which position?" without the reader
 * having to cross-reference the source file.
 *
 * <p>Portfolio fields (id, name, valuation date) are populated for every
 * finding once the file has been parsed; position fields (instrument code,
 * instrument name, valuation weight) are only populated when the finding is
 * row-scoped, i.e. when {@link Finding#rowIndex()} is not null.
 */
public record FindingContext(
        String portfolioId,      // 1_Portfolio_identifying_data (ISIN)
        String portfolioName,    // 3_Portfolio_name
        String valuationDate,    // 6_Valuation_date
        String instrumentCode,   // 14_Identification_code_of_the_instrument
        String instrumentName,   // 17_Instrument_name
        String valuationWeight   // 26_Valuation_weight
) {

    public static final FindingContext EMPTY = new FindingContext(null, null, null, null, null, null);

    public FindingContext withPosition(String instrumentCode, String instrumentName, String valuationWeight) {
        return new FindingContext(portfolioId, portfolioName, valuationDate,
                instrumentCode, instrumentName, valuationWeight);
    }
}
