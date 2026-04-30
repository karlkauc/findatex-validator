package com.findatex.validator.template.eet;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateRuleSet;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.TestFileBuilder;
import com.findatex.validator.validation.ValidationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EetRuleSetTest {

    private final EetTemplate eet = new EetTemplate();

    @Test
    void versionRuleEmitsErrorOnMismatchedHeader() {
        SpecCatalog catalog = eet.specLoaderFor(EetTemplate.V1_1_3).load();
        TemplateRuleSet rs = eet.ruleSetFor(EetTemplate.V1_1_3);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V1.1.0"))
                .build();

        List<Finding> findings = runRule(rs, catalog, file, "EET-XF-VERSION");

        assertThat(findings).extracting(Finding::severity).contains(Severity.ERROR);
    }

    @Test
    void article9TriggersMinEnvAndSocConditionals() {
        SpecCatalog catalog = eet.specLoaderFor(EetTemplate.V1_1_3).load();
        TemplateRuleSet rs = eet.ruleSetFor(EetTemplate.V1_1_3);
        // SFDR product type = 9 (Art. 9), but min-env (45) and min-soc (48) are missing.
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V1.1.3", "27", "9"))
                .build();

        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);
        List<Finding> all = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .toList();

        assertThat(all).extracting(Finding::ruleId)
                .contains("EET-XF-ART9-MIN-ENV", "EET-XF-ART9-MIN-SOC");
    }

    @Test
    void article0TriggersOutOfScopeConditional() {
        SpecCatalog catalog = eet.specLoaderFor(EetTemplate.V1_1_3).load();
        TemplateRuleSet rs = eet.ruleSetFor(EetTemplate.V1_1_3);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V1.1.3", "27", "0"))
                .build();

        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);
        Stream<Finding> findings = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream());

        assertThat(findings.map(Finding::ruleId).toList())
                .contains("EET-XF-SFDR-OUT-OF-SCOPE");
    }

    @Test
    void article0WithArtFieldsPopulatedFiresMustBeAbsent() {
        SpecCatalog catalog = eet.specLoaderFor(EetTemplate.V1_1_3).load();
        TemplateRuleSet rs = eet.ruleSetFor(EetTemplate.V1_1_3);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V1.1.3", "27", "0",
                        "30", "0.5", "41", "0.3", "45", "0.2"))
                .build();

        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);
        List<Finding> findings = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .toList();

        assertThat(findings).extracting(Finding::ruleId)
                .contains("EET-XF-ART30-MUST-BE-ABSENT",
                          "EET-XF-ART41-MUST-BE-ABSENT",
                          "EET-XF-ART45-MUST-BE-ABSENT");
    }

    @Test
    void paiYesTriggersPaiBlockGating() {
        SpecCatalog catalog = eet.specLoaderFor(EetTemplate.V1_1_3).load();
        TemplateRuleSet rs = eet.ruleSetFor(EetTemplate.V1_1_3);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V1.1.3", "33", "Y"))
                .build();

        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_ENTITY);
        long paiErrors = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .filter(f -> f.ruleId().startsWith("EET-XF-PAI-"))
                .count();

        assertThat(paiErrors).isEqualTo(27);
    }

    @Test
    void art8MinUnattributedFiresSplitWarning() {
        SpecCatalog catalog = eet.specLoaderFor(EetTemplate.V1_1_3).load();
        TemplateRuleSet rs = eet.ruleSetFor(EetTemplate.V1_1_3);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V1.1.3", "27", "8",
                        "41", "0.3"))
                .build();

        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);
        List<Finding> findings = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .toList();

        assertThat(findings).extracting(Finding::ruleId)
                .contains("EET-XF-ART8-MIN-SI-SPLIT");
    }

    @Test
    void article6DoesNotTriggerArticle9Conditionals() {
        SpecCatalog catalog = eet.specLoaderFor(EetTemplate.V1_1_3).load();
        TemplateRuleSet rs = eet.ruleSetFor(EetTemplate.V1_1_3);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V1.1.3", "27", "6"))
                .build();

        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);
        List<String> ruleIds = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .map(Finding::ruleId)
                .toList();

        assertThat(ruleIds).doesNotContain("EET-XF-ART9-MIN-ENV", "EET-XF-ART9-MIN-SOC", "EET-XF-SFDR-OUT-OF-SCOPE");
    }

    private static List<Finding> runRule(TemplateRuleSet rs, SpecCatalog catalog, TptFile file, String ruleId) {
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_PRECONTRACT);
        Rule rule = rs.build(catalog, profiles).stream()
                .filter(r -> ruleId.equals(r.id()))
                .findFirst().orElseThrow();
        return rule.evaluate(new ValidationContext(file, catalog, profiles));
    }
}
