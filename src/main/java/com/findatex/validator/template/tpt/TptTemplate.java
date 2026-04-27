package com.findatex.validator.template.tpt;

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
 * {@link TemplateDefinition} for the FinDatEx Tripartite Template (TPT). Phase 0 wraps the
 * existing {@link SpecLoader} for V7; later phases add V6 and the manifest-driven loader.
 */
public final class TptTemplate implements TemplateDefinition {

    public static final TemplateVersion V7_0 = new TemplateVersion(
            TemplateId.TPT,
            "V7.0",
            "TPT V7.0 — 2024-11-25",
            "/spec/tpt/TPT_V7_20241125.xlsx",
            "TPT V7.0",
            LocalDate.of(2024, 11, 25),
            "/spec/tpt/tpt-v7-info.json");

    public static final TemplateVersion V6_0 = new TemplateVersion(
            TemplateId.TPT,
            "V6.0",
            "TPT V6.0 — 2022-03-14",
            "/spec/tpt/TPT_V6_20220314.xlsx",
            "TPT V6.0",
            LocalDate.of(2022, 3, 14),
            "/spec/tpt/tpt-v6-info.json");

    private static final List<TemplateVersion> VERSIONS = List.of(V7_0, V6_0);

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
}
