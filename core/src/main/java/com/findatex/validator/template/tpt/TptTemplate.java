package com.findatex.validator.template.tpt;

import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.external.ExternalValidationConfig.IdentifierRef;
import com.findatex.validator.spec.ManifestDrivenSpecLoader;
import com.findatex.validator.template.api.FindingContextSpec;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateId;
import com.findatex.validator.template.api.TemplateRuleSet;
import com.findatex.validator.template.api.TemplateSpecLoader;
import com.findatex.validator.template.api.TemplateVersion;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@link TemplateDefinition} for the FinDatEx Tripartite Template (TPT). Versions are loaded
 * through the manifest-driven {@link ManifestDrivenSpecLoader}; the two most recent versions
 * (V8.0, V7.0) are bundled.
 */
public final class TptTemplate implements TemplateDefinition {

    public static final TemplateVersion V8_0 = new TemplateVersion(
            TemplateId.TPT,
            "V8.0",
            "TPT V8.0 — 2026-10-08",
            "/spec/tpt/TPT_V8_20261008.xlsx",
            "TPT V8.0",
            LocalDate.of(2026, 10, 8),
            "/spec/tpt/tpt-v8-info.json");

    public static final TemplateVersion V7_0 = new TemplateVersion(
            TemplateId.TPT,
            "V7.0",
            "TPT V7.0 — 2024-11-25",
            "/spec/tpt/TPT_V7_20241125.xlsx",
            "TPT V7.0",
            LocalDate.of(2024, 11, 25),
            "/spec/tpt/tpt-v7-info.json");

    private static final List<TemplateVersion> VERSIONS = List.of(V8_0, V7_0);

    /**
     * TPT V7 column numbering for ISIN/LEI candidates and contextual cross-checks.
     * Type-of-code flag {@code "1"} maps to ISIN (fields 14/68 with their sibling type
     * columns 15/69) and to LEI (seven LEI/type column pairs).
     */
    public static final ExternalValidationConfig EXTERNAL_VALIDATION_V7 = new ExternalValidationConfig(
            List.of(
                    new IdentifierRef("14", "15", "1"),
                    new IdentifierRef("68", "69", "1")),
            List.of(
                    new IdentifierRef("47", "48", "1"),
                    new IdentifierRef("50", "51", "1"),
                    new IdentifierRef("81", "82", "1"),
                    new IdentifierRef("84", "85", "1"),
                    new IdentifierRef("115", "116", "1"),
                    new IdentifierRef("119", "120", "1"),
                    new IdentifierRef("140", "141", "1")),
            "21", "11", "46", "52");

    /**
     * TPT V8 (final, 2026-10-08). ISIN columns and the four issuer/group LEI pairs keep V7's
     * layout (type code {@code "1"} = LEI; V8 adds {@code "2"} = external identifier and
     * redefines {@code "9"} as internal). The fund-issuer, fund-issuer-group and custodian
     * codes (115, 119, 140) must now hold an LEI outright — their former type columns
     * 116/120/141 are "N/A, must be left empty" — so they are single-purpose references.
     */
    public static final ExternalValidationConfig EXTERNAL_VALIDATION_V8 = new ExternalValidationConfig(
            List.of(
                    new IdentifierRef("14", "15", "1"),
                    new IdentifierRef("68", "69", "1")),
            List.of(
                    new IdentifierRef("47", "48", "1"),
                    new IdentifierRef("50", "51", "1"),
                    new IdentifierRef("81", "82", "1"),
                    new IdentifierRef("84", "85", "1"),
                    new IdentifierRef("115", "", ""),
                    new IdentifierRef("119", "", ""),
                    new IdentifierRef("140", "", "")),
            "21", "11", "46", "52");

    /** Default-pointing constant for callers that don't care about version drift (tests, etc.). */
    public static final ExternalValidationConfig EXTERNAL_VALIDATION = EXTERNAL_VALIDATION_V7;

    @Override
    public TemplateId id() {
        return TemplateId.TPT;
    }

    @Override
    public String displayName() {
        return "TPT";
    }

    @Override
    public List<TemplateVersion> versions() {
        return VERSIONS;
    }

    @Override
    public ProfileSet profiles() {
        return TptProfiles.ALL;
    }

    @Override
    public TemplateSpecLoader specLoaderFor(TemplateVersion version) {
        if (!VERSIONS.contains(version)) {
            throw new NoSuchElementException("TPT does not support version " + version.version());
        }
        return ManifestDrivenSpecLoader.fromClasspath(version.manifestResource(), version.resourcePath());
    }

    @Override
    public TemplateRuleSet ruleSetFor(TemplateVersion version) {
        if (!VERSIONS.contains(version)) {
            throw new NoSuchElementException("TPT does not support version " + version.version());
        }
        return new TptRuleSet(version);
    }

    @Override
    public ExternalValidationConfig externalValidationConfigFor(TemplateVersion version) {
        if (version == V8_0) return EXTERNAL_VALIDATION_V8;
        if (version == V7_0) return EXTERNAL_VALIDATION_V7;
        throw new NoSuchElementException("TPT does not support version " + version.version());
    }

    /** TPT carries portfolio-id/name/date in NUM 1/3/6 and ISIN/name/weight in NUM 14/17/26. */
    public static final FindingContextSpec FINDING_CONTEXT =
            new FindingContextSpec("1", "3", "6", "14", "17", "26");

    @Override
    public FindingContextSpec findingContextSpec() {
        return FINDING_CONTEXT;
    }
}
