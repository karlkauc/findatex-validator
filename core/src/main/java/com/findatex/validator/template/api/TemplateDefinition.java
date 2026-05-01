package com.findatex.validator.template.api;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.external.ExternalValidationConfig;

import java.util.List;
import java.util.Set;

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

    /**
     * Narrow the requested profile set to those the producer has opted into via the
     * file's "Reporting" Y/N flags (e.g. EET fields 5–9, EMT/EPT analogues). The orchestrator
     * calls this between header-mapping and rule evaluation: profiles the producer marked
     * {@code N} (or omitted) get suppressed so their PRESENCE/&lt;n&gt;/&lt;PROFILE&gt; rules
     * don't fire — that's how the spec encodes "this file is not an SFDR Entity report,
     * skip those mandatory fields".
     *
     * <p>Default: returns {@code requested} unchanged. Templates without a file-level
     * applicability flag (e.g. TPT) should leave it alone.
     */
    default Set<ProfileKey> activeProfilesForFile(TemplateVersion version,
                                                  TptFile file,
                                                  Set<ProfileKey> requested) {
        return requested;
    }
}
