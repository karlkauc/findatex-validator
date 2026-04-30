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
 * Article-9 Paris-aligned / decarbonisation gating: when NUM=27 (SFDR product type)
 * is "9", NUM=80 ({@code 20570_..._Reduction_In_Carbon_Emission}) and NUM=81
 * ({@code 20580_..._Aligned_With_Paris_Agreement}) must be present. Severity =
 * WARNING until SFDR SME signs off on promotion to ERROR — the spec also says
 * "Could be fulfilled for art 8", so a hard ERROR would over-constrain Art-8 funds
 * that legitimately leave these blank.
 */
class EetArt9ParisDecarbRuleTest {

    static Stream<TemplateVersion> versions() {
        return Stream.of(EetTemplate.V1_1_3, EetTemplate.V1_1_2);
    }

    @ParameterizedTest
    @MethodSource("versions")
    void article9WithEmpty80And81FiresWarningForBoth(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "9"))
                .build();

        List<Finding> parisFindings = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .filter(f -> f.ruleId().startsWith("EET-XF-ART9-PARIS-DECARB-"))
                .toList();

        assertThat(parisFindings).hasSize(2);
        assertThat(parisFindings).extracting(Finding::ruleId)
                .containsExactlyInAnyOrder("EET-XF-ART9-PARIS-DECARB-80",
                                           "EET-XF-ART9-PARIS-DECARB-81");
        assertThat(parisFindings).extracting(Finding::severity).containsOnly(Severity.WARNING);
        assertThat(parisFindings).extracting(Finding::fieldNum)
                .containsExactlyInAnyOrder("80", "81");
    }

    @ParameterizedTest
    @MethodSource("versions")
    void article9WithBothPopulatedFiresNothing(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "9", "80", "Y", "81", "Y"))
                .build();

        boolean anyParis = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .anyMatch(f -> f.ruleId().startsWith("EET-XF-ART9-PARIS-DECARB-"));

        assertThat(anyParis).isFalse();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void article8WithEmpty80And81FiresNothing(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "8"))
                .build();

        boolean anyParis = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .anyMatch(f -> f.ruleId().startsWith("EET-XF-ART9-PARIS-DECARB-"));

        assertThat(anyParis).isFalse();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void outOfScopeWithEmpty80And81FiresNothing(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "0"))
                .build();

        boolean anyParis = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .anyMatch(f -> f.ruleId().startsWith("EET-XF-ART9-PARIS-DECARB-"));

        assertThat(anyParis).isFalse();
    }
}
