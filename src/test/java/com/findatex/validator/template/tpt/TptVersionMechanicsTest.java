package com.findatex.validator.template.tpt;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.TemplateRuleSet;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.TestFileBuilder;
import com.findatex.validator.validation.ValidationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the multi-version plumbing for TPT: V6 manifest loads, the version-aware rule wiring
 * propagates the expected token to {@code TptVersionRule}, and {@code TptTemplate} reports both
 * versions. Cross-field rules and presence checks are template-agnostic and exercised by other
 * suites; this one focuses on the seam introduced for version selection.
 */
class TptVersionMechanicsTest {

    private final TptTemplate tpt = new TptTemplate();

    @Test
    void tptOffersV7AndV6() {
        assertThat(tpt.versions())
                .extracting(TemplateVersion::version)
                .containsExactly("V7.0", "V6.0");
    }

    @Test
    void v6SpecLoaderLoadsAtLeast130Fields() {
        SpecCatalog catalog = tpt.specLoaderFor(TptTemplate.V6_0).load();
        assertThat(catalog.fields().size()).isGreaterThanOrEqualTo(130);
    }

    @Test
    void v6SpecCarriesSolvencyIIFlag() {
        SpecCatalog catalog = tpt.specLoaderFor(TptTemplate.V6_0).load();
        assertThat(catalog.fields()).anySatisfy(field ->
                assertThat(field.flag(TptProfiles.SOLVENCY_II)).isNotNull());
    }

    @Test
    void v6RuleSetEmitsVersionMismatchOnV7Header() {
        SpecCatalog catalog = tpt.specLoaderFor(TptTemplate.V6_0).load();
        TemplateRuleSet rs = tpt.ruleSetFor(TptTemplate.V6_0);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1000", "V7.0 (official) dated 25 November 2024"))
                .build();

        List<Finding> findings = runVersionRule(rs, catalog, file);

        assertThat(findings).extracting(Finding::severity).contains(Severity.ERROR);
        assertThat(findings).extracting(Finding::message)
                .anyMatch(m -> m.contains("V6.0"));
    }

    @Test
    void v7RuleSetIsCleanOnV7Header() {
        SpecCatalog catalog = tpt.specLoaderFor(TptTemplate.V7_0).load();
        TemplateRuleSet rs = tpt.ruleSetFor(TptTemplate.V7_0);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1000", "V7.0 (official) dated 25 November 2024"))
                .build();

        assertThat(runVersionRule(rs, catalog, file)).isEmpty();
    }

    private static List<Finding> runVersionRule(TemplateRuleSet rs, SpecCatalog catalog, TptFile file) {
        Set<com.findatex.validator.template.api.ProfileKey> profiles = Set.of(TptProfiles.SOLVENCY_II);
        Rule version = rs.build(catalog, profiles).stream()
                .filter(r -> "XF-15/TPT_VERSION".equals(r.id()))
                .findFirst().orElseThrow();
        return version.evaluate(new ValidationContext(file, catalog, profiles));
    }
}
