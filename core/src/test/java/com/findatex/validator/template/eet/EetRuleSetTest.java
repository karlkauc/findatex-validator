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
