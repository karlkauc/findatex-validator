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
 * "At-least-one-of" attribution rule for SFDR Art-8 (NUM=41 → {42,43,44}) and
 * Art-9 (NUM=45 → {46,47}) sustainable-investment minimums.
 */
class EetTaxonomyMinSplitRuleTest {

    static Stream<TemplateVersion> versions() {
        return Stream.of(EetTemplate.V1_1_3, EetTemplate.V1_1_2);
    }

    @ParameterizedTest
    @MethodSource("versions")
    void art8MinReportedWithoutAttributionFiresWarning(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "8", "41", "0.3"))
                .build();

        List<Finding> findings = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .filter(f -> "EET-XF-ART8-MIN-SI-SPLIT".equals(f.ruleId()))
                .toList();

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
    }

    @ParameterizedTest
    @MethodSource("versions")
    void art8MinAttributedToTaxonomySatisfiesRule(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "8", "41", "0.3", "44", "Y"))
                .build();

        boolean fired = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .anyMatch(f -> "EET-XF-ART8-MIN-SI-SPLIT".equals(f.ruleId()));
        assertThat(fired).isFalse();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void art8MinBlankDoesNotFire(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "6"))
                .build();

        boolean fired = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .anyMatch(f -> "EET-XF-ART8-MIN-SI-SPLIT".equals(f.ruleId())
                            || "EET-XF-ART9-MIN-ENV-SPLIT".equals(f.ruleId()));
        assertThat(fired).isFalse();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void art9MinReportedWithoutAttributionFiresWarning(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "27", "9", "45", "0.4"))
                .build();

        List<Finding> findings = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .filter(f -> "EET-XF-ART9-MIN-ENV-SPLIT".equals(f.ruleId()))
                .toList();

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
    }
}
