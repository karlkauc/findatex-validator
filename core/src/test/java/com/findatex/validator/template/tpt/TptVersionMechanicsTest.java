package com.findatex.validator.template.tpt;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.Flag;
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
 * Verifies the multi-version plumbing for TPT: the V8 manifest loads, the version-aware rule
 * wiring propagates the expected token to {@code TptVersionRule}, and {@code TptTemplate} reports
 * both bundled versions. Cross-field rules and presence checks are template-agnostic and exercised
 * by other suites; this one focuses on the seam introduced for version selection.
 */
class TptVersionMechanicsTest {

    private final TptTemplate tpt = new TptTemplate();

    @Test
    void tptOffersV8AndV7() {
        assertThat(tpt.versions())
                .extracting(TemplateVersion::version)
                .containsExactly("V8.0", "V7.0");
    }

    @Test
    void v8SpecLoaderLoadsAtLeast140Fields() {
        SpecCatalog catalog = tpt.specLoaderFor(TptTemplate.V8_0).load();
        assertThat(catalog.fields().size()).isGreaterThanOrEqualTo(140);
    }

    @Test
    void v8SpecCarriesTheTwoNewConditionalFields() {
        SpecCatalog catalog = tpt.specLoaderFor(TptTemplate.V8_0).load();
        assertThat(catalog.fields()).extracting(FieldSpec::numKey).contains("150", "151");
    }

    @Test
    void v8FinalSpecLayoutIsParsedCorrectly() {
        // Final V8 (2026-10-08) dropped the helper columns 6-10 and the four EIOPA sub-columns:
        // flag = col 6, CIC = 7..22, NW 675 / SST / IORP = 24 / 25 / 26. A wrong manifest would
        // show up here as empty flags or an empty CIC scope.
        SpecCatalog catalog = tpt.specLoaderFor(TptTemplate.V8_0).load();
        assertThat(catalog.fields()).hasSize(154);

        FieldSpec f48 = catalog.byNumKey("48").orElseThrow();
        assertThat(f48.flag(TptProfiles.SOLVENCY_II)).isEqualTo(Flag.M);
        assertThat(f48.flag(TptProfiles.SST)).isEqualTo(Flag.C);
        assertThat(f48.applicableCic()).contains("CIC0", "CIC1", "CIC3", "CICF").doesNotContain("CIC9");
        assertThat(f48.codification().closedList()).extracting(e -> e.code())
                .containsExactly("1", "2", "9");

        FieldSpec f115 = catalog.byNumKey("115").orElseThrow();
        assertThat(f115.flag(TptProfiles.IORP_EIOPA_ECB)).isEqualTo(Flag.M);
        assertThat(f115.flag(TptProfiles.SST)).isEqualTo(Flag.O);

        FieldSpec f148 = catalog.byNumKey("148").orElseThrow();
        assertThat(f148.numData()).isEqualTo("148_Economic_sector_NACE");
        assertThat(f148.flag(TptProfiles.NW_675)).isEqualTo(Flag.C);

        assertThat(catalog.byNumKey("62").orElseThrow().numData()).isEqualTo("62_Conversion_factor");
        assertThat(catalog.byNumKey("150").orElseThrow().numData()).isEqualTo("150_LTEI_Fund_Eligibility");
        assertThat(catalog.byNumKey("150").orElseThrow().codification().closedList())
                .extracting(e -> e.code()).containsExactly("0", "1", "2");
        assertThat(catalog.byNumKey("56").orElseThrow().codification().closedList())
                .extracting(e -> e.code()).contains("e1", "e2", "e3").doesNotContain("e");
        assertThat(catalog.byNumKey("1000").orElseThrow().codificationRaw())
                .contains("V8.0 (official) dated 8 October 2026");
    }

    @Test
    void v8RetiredFieldsAreNotApplicableAndCarryNoCodification() {
        SpecCatalog catalog = tpt.specLoaderFor(TptTemplate.V8_0).load();
        for (String num : List.of("116", "120", "141", "54", "35", "36")) {
            FieldSpec f = catalog.byNumKey(num).orElseThrow();
            assertThat(f.flag(TptProfiles.SOLVENCY_II)).as("%s is N/A", num).isEqualTo(Flag.NA);
            assertThat(f.codificationRaw()).as("%s has no codification", num).isBlank();
            assertThat(f.applicableCic()).as("%s has no CIC scope", num).isEmpty();
        }
    }

    @Test
    void v8RuleSetAcceptsTheFinalVersionString() {
        SpecCatalog catalog = tpt.specLoaderFor(TptTemplate.V8_0).load();
        TemplateRuleSet rs = tpt.ruleSetFor(TptTemplate.V8_0);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1000", "V8.0 (official) dated 8 October 2026"))
                .build();
        assertThat(runVersionRule(rs, catalog, file)).isEmpty();
    }

    @Test
    void v8SpecCarriesSolvencyIIFlag() {
        SpecCatalog catalog = tpt.specLoaderFor(TptTemplate.V8_0).load();
        assertThat(catalog.fields()).anySatisfy(field ->
                assertThat(field.flag(TptProfiles.SOLVENCY_II)).isNotNull());
    }

    @Test
    void v8RuleSetEmitsVersionMismatchOnV7Header() {
        SpecCatalog catalog = tpt.specLoaderFor(TptTemplate.V8_0).load();
        TemplateRuleSet rs = tpt.ruleSetFor(TptTemplate.V8_0);
        TptFile file = new TestFileBuilder()
                .row(TestFileBuilder.values("1000", "V7.0 (official) dated 25 November 2024"))
                .build();

        List<Finding> findings = runVersionRule(rs, catalog, file);

        assertThat(findings).extracting(Finding::severity).contains(Severity.ERROR);
        assertThat(findings).extracting(Finding::message)
                .anyMatch(m -> m.contains("V8.0"));
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
