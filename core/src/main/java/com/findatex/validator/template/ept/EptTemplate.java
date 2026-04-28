package com.findatex.validator.template.ept;

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
 * {@link TemplateDefinition} for the European PRIIPs Template (EPT). The profile dimension is
 * version-dependent: V2.0 ships PRIIPs+UCITS-KIID, V2.1 ships PRIIPs+UK. Sheet names in the
 * bundled XLSX have a trailing space ("EPT 2.1 ") — preserved verbatim in the manifest.
 */
public final class EptTemplate implements TemplateDefinition {

    public static final TemplateVersion V2_1 = new TemplateVersion(
            TemplateId.EPT,
            "V2.1",
            "EPT V2.1 — 2022-10-12",
            "/spec/ept/EPT_V2_1_20221012.xlsx",
            "EPT 2.1 ",
            LocalDate.of(2022, 10, 12),
            "/spec/ept/ept-v21-info.json");

    public static final TemplateVersion V2_0 = new TemplateVersion(
            TemplateId.EPT,
            "V2.0",
            "EPT V2.0 — 2022-02-15",
            "/spec/ept/EPT_V2_0_20220215.xlsx",
            "EPT 2.0 ",
            LocalDate.of(2022, 2, 15),
            "/spec/ept/ept-v20-info.json");

    private static final List<TemplateVersion> VERSIONS = List.of(V2_1, V2_0);

    @Override
    public TemplateId id() {
        return TemplateId.EPT;
    }

    @Override
    public String displayName() {
        return "EPT";
    }

    @Override
    public List<TemplateVersion> versions() {
        return VERSIONS;
    }

    @Override
    public ProfileSet profiles() {
        return EptProfiles.ALL;
    }

    @Override
    public ProfileSet profilesFor(TemplateVersion version) {
        if (version == V2_1) return EptProfiles.V2_1;
        if (version == V2_0) return EptProfiles.V2_0;
        throw new NoSuchElementException("EPT does not support version " + version.version());
    }

    @Override
    public TemplateSpecLoader specLoaderFor(TemplateVersion version) {
        if (!VERSIONS.contains(version)) {
            throw new NoSuchElementException("EPT does not support version " + version.version());
        }
        return ManifestDrivenSpecLoader.fromClasspath(version.manifestResource(), version.resourcePath());
    }

    @Override
    public TemplateRuleSet ruleSetFor(TemplateVersion version) {
        if (!VERSIONS.contains(version)) {
            throw new NoSuchElementException("EPT does not support version " + version.version());
        }
        return new EptRuleSet(version);
    }
}
