package com.findatex.validator.template.api;

import com.findatex.validator.external.ExternalValidationConfig;

import java.util.List;

/**
 * Describes one FinDatEx template (TPT, EET, EMT, EPT) and exposes its versions, profiles,
 * and per-version spec/rule providers. Implementations live under {@code com.findatex.validator.template.<id>/}.
 */
public interface TemplateDefinition {

    TemplateId id();

    String displayName();

    /** Versions ordered most-recent first. {@link #latest()} returns {@code versions().get(0)}. */
    List<TemplateVersion> versions();

    default TemplateVersion latest() {
        List<TemplateVersion> v = versions();
        if (v.isEmpty()) {
            throw new IllegalStateException("Template " + id() + " has no registered versions");
        }
        return v.get(0);
    }

    ProfileSet profiles();

    /**
     * Version-specific profile set. Default returns {@link #profiles()} — override in templates
     * whose profile dimension changes between bundled versions (currently only EPT, where
     * V2.0 ships UCITS-KIID columns and V2.1 ships UK columns instead).
     */
    default ProfileSet profilesFor(TemplateVersion version) {
        return profiles();
    }

    TemplateSpecLoader specLoaderFor(TemplateVersion version);

    TemplateRuleSet ruleSetFor(TemplateVersion version);

    /**
     * Per-version configuration of the columns the GLEIF/OpenFIGI live-lookup pipeline should
     * inspect. Default is {@link ExternalValidationConfig#empty()} — templates that want online
     * validation override this and declare their ISIN/LEI columns. An empty config disables the
     * external pipeline for the template/version pair.
     */
    default ExternalValidationConfig externalValidationConfigFor(TemplateVersion version) {
        return ExternalValidationConfig.empty();
    }

    /**
     * Maps {@link com.findatex.validator.validation.Finding} context slots to the data-file
     * field {@code numKey}s holding those values. Default is {@link FindingContextSpec#EMPTY},
     * which leaves findings unannotated; templates that have a portfolio/position notion
     * (currently TPT, plus EMT's issuer/instrument shape) should override.
     */
    default FindingContextSpec findingContextSpec() {
        return FindingContextSpec.EMPTY;
    }
}
