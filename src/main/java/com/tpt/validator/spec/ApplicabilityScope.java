package com.tpt.validator.spec;

/**
 * Captures any template-specific row-applicability dimension. TPT uses CIC codes
 * ({@link CicApplicabilityScope}); EET/EMT/EPT have no such dimension and use
 * {@link EmptyApplicabilityScope}. Pattern-match the concrete subtype to access
 * template-specific applicability checks.
 */
public sealed interface ApplicabilityScope permits CicApplicabilityScope, EmptyApplicabilityScope {

    /** True if the field applies to every row regardless of any contextual codes. */
    boolean appliesAlways();
}
