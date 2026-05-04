package com.findatex.validator.validation;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.validation.rules.crossfield.CompleteScrDeliveryRule;
import com.findatex.validator.validation.rules.crossfield.CustodianPairRule;
import com.findatex.validator.validation.rules.crossfield.PikRule;
import com.findatex.validator.validation.rules.crossfield.TptVersionRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.findatex.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

class CrossFieldRulesTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    private ValidationContext ctx(TptFile file) {
        return new ValidationContext(file, CATALOG, new java.util.HashSet<>(java.util.Arrays.asList(TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB, TptProfiles.NW_675, TptProfiles.SST)));
    }

    // ---------------------------------------------------- XF-01: SCR delivery

    @Test
    void scrDeliveryYesRequiresAllScrFields() {
        TptFile file = new TestFileBuilder()
                .row(values(
                        "11", "Y",                  // CompleteSCRDelivery = Y
                        "12", "FR12",
                        "97", "0.01",
                        "98", "0.02"
                        // 99..105b deliberately missing
                ))
                .build();

        List<Finding> findings = new CompleteScrDeliveryRule().evaluate(ctx(file));

        assertThat(findings).extracting(Finding::fieldNum)
                .contains("99", "100", "101", "102", "103", "104", "105", "105a", "105b");
        assertThat(findings).allSatisfy(f -> assertThat(f.severity()).isEqualTo(Severity.ERROR));
    }

    @Test
    void scrDeliveryNoIsClean() {
        TptFile file = new TestFileBuilder()
                .row(values("11", "N", "12", "FR12"))
                .build();
        assertThat(new CompleteScrDeliveryRule().evaluate(ctx(file))).isEmpty();
    }

    // ----------------------------------------------- XF-09: Custodian pair (140/141)

    @Test
    void custodianCodePresentRequiresType() {
        TptFile file = new TestFileBuilder()
                .row(values(
                        "12", "FR12",
                        "140", "529900D6BF99LW9R2E68",  // code without type
                        "141", ""
                ))
                .build();

        List<Finding> findings = new CustodianPairRule().evaluate(ctx(file));

        assertThat(findings).extracting(Finding::fieldNum).contains("141");
        assertThat(findings).extracting(Finding::severity).contains(Severity.ERROR);
    }

    @Test
    void custodianTypeWithoutCodeIsWarning() {
        TptFile file = new TestFileBuilder()
                .row(values("12", "FR12", "140", "", "141", "1"))
                .build();

        List<Finding> findings = new CustodianPairRule().evaluate(ctx(file));

        assertThat(findings).extracting(Finding::fieldNum).contains("140");
        assertThat(findings).extracting(Finding::severity).contains(Severity.WARNING);
    }

    @Test
    void custodianBothPresentIsClean() {
        TptFile file = new TestFileBuilder()
                .row(values("12", "FR12", "140", "529900D6BF99LW9R2E68", "141", "1"))
                .build();
        assertThat(new CustodianPairRule().evaluate(ctx(file))).isEmpty();
    }

    // -------------------------------------------- XF-13: PIK guideline cases

    @Test
    void pikCase1RequiresCouponAndRedemptionFields() {
        TptFile file = new TestFileBuilder()
                .row(values(
                        "12", "FR82",      // CIC 8 (Loans) — meaningful PIK target
                        "146", "1"
                        // 32, 38, 39, 40, 41 deliberately missing
                ))
                .build();

        List<Finding> findings = new PikRule().evaluate(ctx(file));

        assertThat(findings).extracting(Finding::fieldNum)
                .contains("32", "38", "39", "40", "41");
        assertThat(findings).extracting(Finding::ruleId)
                .anyMatch(id -> id.contains("PIK_CASE_1_FIELD_32"));
    }

    @Test
    void pikCase2RequiresCouponRateAndRedemptionRate() {
        TptFile file = new TestFileBuilder()
                .row(values("12", "FR22", "146", "2"))   // CIC 2 (corporate bonds)
                .build();
        List<Finding> findings = new PikRule().evaluate(ctx(file));
        assertThat(findings).extracting(Finding::fieldNum).contains("33", "41");
    }

    @Test
    void pikCase3RequiresCouponRateAndFrequency() {
        TptFile file = new TestFileBuilder()
                .row(values("12", "FR22", "146", "3"))
                .build();
        List<Finding> findings = new PikRule().evaluate(ctx(file));
        assertThat(findings).extracting(Finding::fieldNum).contains("33", "38");
    }

    @Test
    void pikInvalidValueProducesError() {
        TptFile file = new TestFileBuilder()
                .row(values("12", "FR22", "146", "9"))
                .build();
        List<Finding> findings = new PikRule().evaluate(ctx(file));
        assertThat(findings).extracting(Finding::severity).contains(Severity.ERROR);
        assertThat(findings).extracting(Finding::message)
                .anyMatch(m -> m.contains("must be one of"));
    }

    @Test
    void pikOnEquityIsWarning() {
        TptFile file = new TestFileBuilder()
                .row(values("12", "DE31", "146", "0"))   // CIC 3 (equity) — not bond/loan
                .build();
        List<Finding> findings = new PikRule().evaluate(ctx(file));
        assertThat(findings).extracting(Finding::severity).contains(Severity.WARNING);
    }

    @Test
    void pikZeroOnBondIsClean() {
        TptFile file = new TestFileBuilder()
                .row(values("12", "FR22", "146", "0"))
                .build();
        assertThat(new PikRule().evaluate(ctx(file))).isEmpty();
    }

    // ------------------------------------------------------ XF-15: TPT version

    @Test
    void tptVersionV6IsError() {
        TptFile file = new TestFileBuilder()
                .row(values("1000", "V6.0 (official) dated 10 January 2022"))
                .build();
        List<Finding> findings = new TptVersionRule("V7.0").evaluate(ctx(file));
        assertThat(findings).extracting(Finding::severity).contains(Severity.ERROR);
        assertThat(findings).extracting(Finding::fieldNum).contains("1000");
    }

    @Test
    void tptVersionMissingProducesInfo() {
        TptFile file = new TestFileBuilder()
                .row(values("12", "FR12"))
                .build();
        List<Finding> findings = new TptVersionRule("V7.0").evaluate(ctx(file));
        assertThat(findings).extracting(Finding::severity).containsOnly(Severity.INFO);
    }

    @Test
    void tptVersionV7IsClean() {
        TptFile file = new TestFileBuilder()
                .row(values("1000", "V7.0 (official) dated 25 November 2024"))
                .build();
        assertThat(new TptVersionRule("V7.0").evaluate(ctx(file))).isEmpty();
    }
}
