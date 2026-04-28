package com.findatex.validator.template.eet;

import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.external.ExternalValidationConfig.IdentifierRef;
import com.findatex.validator.spec.ManifestDrivenSpecLoader;
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
 * {@link TemplateDefinition} for the European ESG Template (EET). Two bundled versions:
 * V1.1.3 (latest, ESMA guidelines update) and V1.1.2 (preserved for backwards-compat with
 * previously-published instance files).
 */
public final class EetTemplate implements TemplateDefinition {

    public static final TemplateVersion V1_1_3 = new TemplateVersion(
            TemplateId.EET,
            "V1.1.3",
            "EET V1.1.3 — 2024-10-04",
            "/spec/eet/EET_V1_1_3_20260410.xlsx",
            "EET",
            LocalDate.of(2024, 10, 4),
            "/spec/eet/eet-v113-info.json");

    public static final TemplateVersion V1_1_2 = new TemplateVersion(
            TemplateId.EET,
            "V1.1.2",
            "EET V1.1.2 — 2023-12-05",
            "/spec/eet/EET_V1_1_2_20231205.xlsx",
            "EET",
            LocalDate.of(2023, 12, 5),
            "/spec/eet/eet-v112-info.json");

    private static final List<TemplateVersion> VERSIONS = List.of(V1_1_3, V1_1_2);

    /**
     * V1.1.2 and V1.1.3 share the relevant field numbering. The catalog stores fields by their
     * sequential counter (column A of the spec sheet), not by the {@code 20000_*} data-name
     * prefix — hence the small numeric keys here. Polymorphic instrument identifier 23 (a.k.a.
     * "20000_Financial_Instrument_Identifying_Data") is paired with its type-of-code flag 24
     * (a.k.a. "20010_..."): {@code "1"} = ISIN, {@code "10"} = LEI. The manufacturer LEI lives
     * in field 13 with a separate "L"/"N" flag in 12, and the EET producer LEI (field 3, a.k.a.
     * "00030_EET_Producer_LEI") is alphanum-only with no type flag.
     */
    public static final ExternalValidationConfig EXTERNAL_VALIDATION = new ExternalValidationConfig(
            List.of(new IdentifierRef("23", "24", "1")),
            List.of(
                    new IdentifierRef("23", "24", "10"),
                    new IdentifierRef("13", "12", "L"),
                    new IdentifierRef("3", "", "")),
            "", "", "", "");

    @Override
    public TemplateId id() {
        return TemplateId.EET;
    }

    @Override
    public String displayName() {
        return "EET";
    }

    @Override
    public List<TemplateVersion> versions() {
        return VERSIONS;
    }

    @Override
    public ProfileSet profiles() {
        return EetProfiles.ALL;
    }

    @Override
    public TemplateSpecLoader specLoaderFor(TemplateVersion version) {
        if (!VERSIONS.contains(version)) {
            throw new NoSuchElementException("EET does not support version " + version.version());
        }
        return ManifestDrivenSpecLoader.fromClasspath(version.manifestResource(), version.resourcePath());
    }

    @Override
    public TemplateRuleSet ruleSetFor(TemplateVersion version) {
        if (!VERSIONS.contains(version)) {
            throw new NoSuchElementException("EET does not support version " + version.version());
        }
        return new EetRuleSet(version);
    }

    @Override
    public ExternalValidationConfig externalValidationConfigFor(TemplateVersion version) {
        if (!VERSIONS.contains(version)) {
            throw new NoSuchElementException("EET does not support version " + version.version());
        }
        return EXTERNAL_VALIDATION;
    }
}
