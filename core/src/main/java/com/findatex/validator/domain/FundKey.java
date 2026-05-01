package com.findatex.validator.domain;

/**
 * Identifies a fund within a TPT file by the tuple
 * (Portfolio_identifying_data, Valuation_date) — fields 1 and 6.
 * Either segment may be {@code null} for files where the column is absent
 * or blank on continuation rows.
 */
public record FundKey(String portfolioId, String valuationDate) {

    public boolean isEmpty() {
        return (portfolioId == null || portfolioId.isBlank())
            && (valuationDate == null || valuationDate.isBlank());
    }
}
