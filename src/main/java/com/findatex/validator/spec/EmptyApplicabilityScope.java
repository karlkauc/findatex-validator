package com.findatex.validator.spec;

/** Singleton scope used by templates that have no CIC-style applicability dimension (EET/EMT/EPT). */
public final class EmptyApplicabilityScope implements ApplicabilityScope {

    public static final EmptyApplicabilityScope INSTANCE = new EmptyApplicabilityScope();

    private EmptyApplicabilityScope() {
    }

    @Override
    public boolean appliesAlways() {
        return true;
    }
}
