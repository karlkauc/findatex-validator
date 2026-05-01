package com.findatex.validator.template.api;

/**
 * Per-template mapping from {@link com.findatex.validator.validation.Finding}'s context slots
 * (portfolio id/name/date, position ISIN/name/weight) to the field {@code numKey}s that hold
 * those values in a given template's data file. Lets {@link com.findatex.validator.validation.FindingEnricher}
 * enrich findings with template-appropriate context instead of hard-coding TPT field numbers
 * (which would surface the EMT version under {@code portfolioId}, the Producer LEI under
 * {@code portfolioName} and so on).
 *
 * <p>Any field that does not exist in a given template should be {@code null}; the enricher
 * leaves the corresponding context slot empty rather than reaching for a wrong field.</p>
 */
public record FindingContextSpec(
        String portfolioIdNumKey,
        String portfolioNameNumKey,
        String valuationDateNumKey,
        String instrumentCodeNumKey,
        String instrumentNameNumKey,
        String valuationWeightNumKey) {

    /** No context — findings get neither portfolio nor position annotations. */
    public static final FindingContextSpec EMPTY =
            new FindingContextSpec(null, null, null, null, null, null);

    /** {@code true} if a portfolio identifier is declared, so the file is split into per-fund
     *  groups. When {@code false} the whole file is treated as a single group. */
    public boolean hasPortfolioGrouping() {
        return portfolioIdNumKey != null;
    }
}
