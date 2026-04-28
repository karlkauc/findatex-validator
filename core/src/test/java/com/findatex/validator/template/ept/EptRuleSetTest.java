package com.findatex.validator.template.ept;

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

import static org.assertj.core.api.Assertions.assertThat;

class EptRuleSetTest {

    private final EptTemplate ept = new EptTemplate();

    @Test
    void buildProducesRulesIncludingVersionRule() {
        SpecCatalog catalog = ept.specLoaderFor(EptTemplate.V2_1).load();
        TemplateRuleSet rs = ept.ruleSetFor(EptTemplate.V2_1);

        List<Rule> rules = rs.build(catalog, Set.of(EptProfiles.PRIIPS_KID));

        assertThat(rules).extracting(Rule::id).contains("EPT-XF-VERSION");
        assertThat(rules.size()).isGreaterThan(catalog.fields().size());
    }

    @Test
    void v21VersionRuleAcceptsCompactV21Token() {
        SpecCatalog catalog = ept.specLoaderFor(EptTemplate.V2_1).load();
        TemplateRuleSet rs = ept.ruleSetFor(EptTemplate.V2_1);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V21"))
                .build();

        assertThat(runVersionRule(rs, catalog, file)).isEmpty();
    }

    @Test
    void v21VersionRuleAcceptsV21UkVariant() {
        // The EPT spec explicitly allows "V21" or "V21UK" in field 1.
        SpecCatalog catalog = ept.specLoaderFor(EptTemplate.V2_1).load();
        TemplateRuleSet rs = ept.ruleSetFor(EptTemplate.V2_1);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V21UK"))
                .build();

        assertThat(runVersionRule(rs, catalog, file)).isEmpty();
    }

    @Test
    void v21VersionRuleErrorsOnV20Header() {
        SpecCatalog catalog = ept.specLoaderFor(EptTemplate.V2_1).load();
        TemplateRuleSet rs = ept.ruleSetFor(EptTemplate.V2_1);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V20"))
                .build();

        List<Finding> findings = runVersionRule(rs, catalog, file);

        assertThat(findings).extracting(Finding::severity).contains(Severity.ERROR);
        assertThat(findings).extracting(Finding::message).anyMatch(m -> m.contains("V21"));
    }

    @Test
    void v20VersionRuleAcceptsV20Header() {
        SpecCatalog catalog = ept.specLoaderFor(EptTemplate.V2_0).load();
        TemplateRuleSet rs = ept.ruleSetFor(EptTemplate.V2_0);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V20"))
                .build();

        assertThat(runVersionRule(rs, catalog, file)).isEmpty();
    }

    @Test
    void emptyVersionFieldEmitsInfo() {
        SpecCatalog catalog = ept.specLoaderFor(EptTemplate.V2_1).load();
        TemplateRuleSet rs = ept.ruleSetFor(EptTemplate.V2_1);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("999", "irrelevant"))
                .build();

        List<Finding> findings = runVersionRule(rs, catalog, file);

        assertThat(findings).extracting(Finding::severity).containsOnly(Severity.INFO);
    }

    private static List<Finding> runVersionRule(TemplateRuleSet rs, SpecCatalog catalog, TptFile file) {
        Set<ProfileKey> profiles = Set.of(EptProfiles.PRIIPS_KID);
        Rule rule = rs.build(catalog, profiles).stream()
                .filter(r -> "EPT-XF-VERSION".equals(r.id()))
                .findFirst().orElseThrow();
        return rule.evaluate(new ValidationContext(file, catalog, profiles));
    }
}
