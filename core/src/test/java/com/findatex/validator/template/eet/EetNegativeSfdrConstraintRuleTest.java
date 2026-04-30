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
 * Verifies the negative SFDR constraint: when NUM=27 (SFDR product type) is
 * "0" (out-of-scope), Art-8/Art-9 fields must be empty. Severity = WARNING.
 */
class EetNegativeSfdrConstraintRuleTest {

    static Stream<TemplateVersion> versions() {
        return Stream.of(EetTemplate.V1_1_3, EetTemplate.V1_1_2);
    }

    @ParameterizedTest
    @MethodSource("versions")
    void outOfScopeWithArtFieldsPopulatedFiresMustBeAbsent(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "0",
                            "30", "0.5", "41", "0.3", "45", "0.2"))
                .build();

        List<Finding> findings = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .toList();

        assertThat(findings).extracting(Finding::ruleId)
                .contains("EET-XF-ART30-MUST-BE-ABSENT",
                          "EET-XF-ART41-MUST-BE-ABSENT",
                          "EET-XF-ART45-MUST-BE-ABSENT");
        assertThat(findings.stream()
                .filter(f -> f.ruleId().startsWith("EET-XF-ART") && f.ruleId().endsWith("-MUST-BE-ABSENT"))
                .map(Finding::severity)
                .distinct().toList())
                .containsExactly(Severity.WARNING);
    }

    @ParameterizedTest
    @MethodSource("versions")
    void outOfScopeWithArtFieldsEmptyFiresNothing(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "0", "28", "6"))
                .build();

        List<String> mustBeAbsentIds = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .map(Finding::ruleId)
                .filter(id -> id.startsWith("EET-XF-ART") && id.endsWith("-MUST-BE-ABSENT"))
                .toList();

        assertThat(mustBeAbsentIds).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void article8WithArtFieldsPopulatedDoesNotFireMustBeAbsent(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "8",
                            "30", "0.5", "40", "Y", "41", "0.3"))
                .build();

        List<String> mustBeAbsentIds = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .map(Finding::ruleId)
                .filter(id -> id.startsWith("EET-XF-ART") && id.endsWith("-MUST-BE-ABSENT"))
                .toList();

        assertThat(mustBeAbsentIds).isEmpty();
    }
}
