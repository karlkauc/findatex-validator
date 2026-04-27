package com.findatex.validator.template.emt;

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

class EmtRuleSetTest {

    private final EmtTemplate emt = new EmtTemplate();

    @Test
    void buildProducesRulesIncludingVersionRule() {
        SpecCatalog catalog = emt.specLoaderFor(EmtTemplate.V4_3).load();
        TemplateRuleSet rs = emt.ruleSetFor(EmtTemplate.V4_3);

        List<Rule> rules = rs.build(catalog, Set.of(EmtProfiles.EMT_BASE));

        assertThat(rules).extracting(Rule::id).contains("EMT-XF-VERSION");
        assertThat(rules.size()).isGreaterThan(catalog.fields().size()); // at least Format per field + version rule
    }

    @Test
    void v43VersionRuleErrorsOnV42Header() {
        SpecCatalog catalog = emt.specLoaderFor(EmtTemplate.V4_3).load();
        TemplateRuleSet rs = emt.ruleSetFor(EmtTemplate.V4_3);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V4.2"))
                .build();

        List<Finding> findings = runVersionRule(rs, catalog, file);

        assertThat(findings).extracting(Finding::severity).contains(Severity.ERROR);
        assertThat(findings).extracting(Finding::message).anyMatch(m -> m.contains("V4.3"));
    }

    @Test
    void v43VersionRuleAcceptsMatchingHeader() {
        SpecCatalog catalog = emt.specLoaderFor(EmtTemplate.V4_3).load();
        TemplateRuleSet rs = emt.ruleSetFor(EmtTemplate.V4_3);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V4.3"))
                .build();

        assertThat(runVersionRule(rs, catalog, file)).isEmpty();
    }

    @Test
    void v42VersionRuleAcceptsV42Header() {
        SpecCatalog catalog = emt.specLoaderFor(EmtTemplate.V4_2).load();
        TemplateRuleSet rs = emt.ruleSetFor(EmtTemplate.V4_2);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1", "V4.2"))
                .build();

        assertThat(runVersionRule(rs, catalog, file)).isEmpty();
    }

    @Test
    void emptyVersionFieldEmitsInfo() {
        SpecCatalog catalog = emt.specLoaderFor(EmtTemplate.V4_3).load();
        TemplateRuleSet rs = emt.ruleSetFor(EmtTemplate.V4_3);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("999", "irrelevant"))
                .build();

        List<Finding> findings = runVersionRule(rs, catalog, file);

        assertThat(findings).extracting(Finding::severity).containsOnly(Severity.INFO);
    }

    private static List<Finding> runVersionRule(TemplateRuleSet rs, SpecCatalog catalog, TptFile file) {
        Set<ProfileKey> profiles = Set.of(EmtProfiles.EMT_BASE);
        Rule rule = rs.build(catalog, profiles).stream()
                .filter(r -> "EMT-XF-VERSION".equals(r.id()))
                .findFirst().orElseThrow();
        return rule.evaluate(new ValidationContext(file, catalog, profiles));
    }
}
