package com.findatex.validator.template.eet;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateRuleSet;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.TestFileBuilder;
import com.findatex.validator.validation.ValidationContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.findatex.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pre-Contractual Disclosure For Multi-Option Products gating: when NUM=27
 * ({@code 20040_..._SFDR_Product_Type}) OR NUM=28 ({@code 20050_..._SFDR_Product_Type_Eligible})
 * is "8" or "9", the PCDFP link (NUM=35) and its production date (NUM=36)
 * must be present. Identical comment text in V1.1.2 + V1.1.3 — V1.1.3 added
 * a C-flag for {@code SFDR_PRECONTRACT}, but the cross-field rule fires in
 * both versions because the spec's conditional language is identical.
 * Severity = WARNING ("Could be provided for art6 under insurers demand"
 * — i.e. enforcement is conventional, not absolute). PENDING SME SIGN-OFF.
 */
class EetPcdfpDriftRuleTest {

    static Stream<TemplateVersion> versions() {
        return Stream.of(EetTemplate.V1_1_3, EetTemplate.V1_1_2);
    }

    @ParameterizedTest
    @MethodSource("versions")
    void article9On27FiresPcdfpForBothTargets(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "9"))
                .build();

        List<Finding> pcdfp = pcdfpFindings(rs, catalog, file, profiles);

        assertThat(pcdfp).hasSize(2);
        assertThat(pcdfp).extracting(Finding::ruleId)
                .containsExactlyInAnyOrder("EET-XF-PCDFP-35", "EET-XF-PCDFP-36");
        assertThat(pcdfp).extracting(Finding::severity).containsOnly(Severity.WARNING);
        assertThat(pcdfp).extracting(Finding::fieldNum).containsExactlyInAnyOrder("35", "36");
    }

    @ParameterizedTest
    @MethodSource("versions")
    void article9On28FiresPcdfpForBothTargets(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        // NUM 27 out-of-scope, NUM 28 = "9" — trigger via the eligibility field.
        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "0", "28", "9"))
                .build();

        List<Finding> pcdfp = pcdfpFindings(rs, catalog, file, profiles);

        assertThat(pcdfp).extracting(Finding::ruleId)
                .containsExactlyInAnyOrder("EET-XF-PCDFP-35", "EET-XF-PCDFP-36");
    }

    @ParameterizedTest
    @MethodSource("versions")
    void article8On27AndArticle9On28FiresOncePerTarget(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        // Both source fields trigger — must NOT double-emit per target.
        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "8", "28", "9"))
                .build();

        List<Finding> pcdfp = pcdfpFindings(rs, catalog, file, profiles);

        assertThat(pcdfp).hasSize(2);
        assertThat(pcdfp).extracting(Finding::fieldNum).containsExactlyInAnyOrder("35", "36");
    }

    @ParameterizedTest
    @MethodSource("versions")
    void outOfScopeOnBothFiresNothing(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "0", "28", "0"))
                .build();

        assertThat(pcdfpFindings(rs, catalog, file, profiles)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void article9With35PopulatedFiresOnly36(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "9",
                        "35", "https://findatex.example/sample/pre-contractual/X/EN.pdf"))
                .build();

        List<Finding> pcdfp = pcdfpFindings(rs, catalog, file, profiles);

        assertThat(pcdfp).hasSize(1);
        assertThat(pcdfp.get(0).ruleId()).isEqualTo("EET-XF-PCDFP-36");
    }

    @ParameterizedTest
    @MethodSource("versions")
    void article9WithBothPopulatedFiresNothing(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "9",
                        "35", "https://findatex.example/sample/pre-contractual/X/EN.pdf",
                        "36", "2026-03-31"))
                .build();

        assertThat(pcdfpFindings(rs, catalog, file, profiles)).isEmpty();
    }

    private static List<Finding> pcdfpFindings(TemplateRuleSet rs, SpecCatalog catalog,
                                               TptFile file, Set<ProfileKey> profiles) {
        return rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .filter(f -> f.ruleId().startsWith("EET-XF-PCDFP-"))
                .toList();
    }
}
