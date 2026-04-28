package com.findatex.validator.template.emt;

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
 * {@link TemplateDefinition} for the European MiFID Template (EMT). V4.3 (latest) and
 * V4.2 (preserved for archived production data). Note: V4.2's bundled XLSX names the data
 * sheet "EMT V4.1" — the {@code emt-v42-info.json} manifest carries that exact sheet name
 * even though the file release identifier is V4.2.
 */
public final class EmtTemplate implements TemplateDefinition {

    public static final TemplateVersion V4_3 = new TemplateVersion(
            TemplateId.EMT,
            "V4.3",
            "EMT V4.3 — 2025-12-17",
            "/spec/emt/EMT_V4_3_20251217.xlsx",
            "EMT V4.3",
            LocalDate.of(2025, 12, 17),
            "/spec/emt/emt-v43-info.json");

    public static final TemplateVersion V4_2 = new TemplateVersion(
            TemplateId.EMT,
            "V4.2",
            "EMT V4.2 — 2024-04-22",
            "/spec/emt/EMT_V4_2_20240422.xlsx",
            "EMT V4.1",
            LocalDate.of(2024, 4, 22),
            "/spec/emt/emt-v42-info.json");

    private static final List<TemplateVersion> VERSIONS = List.of(V4_3, V4_2);

    /**
     * V4.2 and V4.3 share the relevant column numbering. The catalog stores fields by their
     * sequential counter (column A), not by the {@code 00010_*} data-name prefix. Field 9
     * (a.k.a. "00010_Financial_Instrument_Identifying_Data") is the polymorphic identifier;
     * paired with type flag 10 (a.k.a. "00020_..."): {@code "1"} = ISIN, {@code "10"} = LEI.
     * Field 20 ("00073_Financial_Instrument_Manufacturer_LEI") and field 3 ("00003_EMT_Producer_LEI")
     * are alphanum-only LEI columns with no type flag.
     */
    public static final ExternalValidationConfig EXTERNAL_VALIDATION = new ExternalValidationConfig(
            List.of(new IdentifierRef("9", "10", "1")),
            List.of(
                    new IdentifierRef("9", "10", "10"),
                    new IdentifierRef("20", "", ""),
                    new IdentifierRef("3", "", "")),
            "", "", "", "");

    @Override
    public TemplateId id() {
        return TemplateId.EMT;
    }

    @Override
    public String displayName() {
        return "EMT";
    }

    @Override
    public List<TemplateVersion> versions() {
        return VERSIONS;
    }

    @Override
    public ProfileSet profiles() {
        return EmtProfiles.ALL;
    }

    @Override
    public TemplateSpecLoader specLoaderFor(TemplateVersion version) {
        if (!VERSIONS.contains(version)) {
            throw new NoSuchElementException("EMT does not support version " + version.version());
        }
        return ManifestDrivenSpecLoader.fromClasspath(version.manifestResource(), version.resourcePath());
    }

    @Override
    public TemplateRuleSet ruleSetFor(TemplateVersion version) {
        if (!VERSIONS.contains(version)) {
            throw new NoSuchElementException("EMT does not support version " + version.version());
        }
        return new EmtRuleSet(version);
    }

    @Override
    public ExternalValidationConfig externalValidationConfigFor(TemplateVersion version) {
        if (!VERSIONS.contains(version)) {
            throw new NoSuchElementException("EMT does not support version " + version.version());
        }
        return EXTERNAL_VALIDATION;
    }
}
