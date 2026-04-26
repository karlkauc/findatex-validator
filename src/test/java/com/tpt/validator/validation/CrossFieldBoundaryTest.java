package com.tpt.validator.validation;

import com.tpt.validator.domain.TptFile;
import com.tpt.validator.spec.Profile;
import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.spec.SpecLoader;
import com.tpt.validator.validation.rules.crossfield.CashPercentageRule;
import com.tpt.validator.validation.rules.crossfield.DateOrderRule;
import com.tpt.validator.validation.rules.crossfield.MaturityAfterReportingRule;
import com.tpt.validator.validation.rules.crossfield.NavConsistencyRule;
import com.tpt.validator.validation.rules.crossfield.PositionWeightSumRule;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static com.tpt.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

/** Boundary cases for tolerance-based cross-field rules. */
class CrossFieldBoundaryTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    private ValidationContext ctx(TptFile file) {
        return new ValidationContext(file, CATALOG, EnumSet.allOf(Profile.class));
    }

    // --------------------------------------------- XF-04 PositionWeightSum

    @Test
    void positionWeightExactlyOneIsClean() {
        TptFile f = new TestFileBuilder()
                .row(values("26", "0.5"))
                .row(values("26", "0.5"))
                .build();
        assertThat(new PositionWeightSumRule().evaluate(ctx(f))).isEmpty();
    }

    @Test
    void positionWeightInsideToleranceIsClean() {
        // tolerance = 0.02 (±2 %); 1.019 is comfortably within.
        TptFile f = new TestFileBuilder()
                .row(values("26", "1.019"))
                .build();
        assertThat(new PositionWeightSumRule().evaluate(ctx(f))).isEmpty();
    }

    @Test
    void positionWeightJustBeyondToleranceIsFlagged() {
        TptFile f = new TestFileBuilder()
                .row(values("26", "1.025"))
                .build();
        List<Finding> findings = new PositionWeightSumRule().evaluate(ctx(f));
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void positionWeightWithMalformedNumberIsIgnored() {
        TptFile f = new TestFileBuilder()
                .row(values("26", "0.5"))
                .row(values("26", "abc"))    // ignored — format rule's domain
                .row(values("26", "0.5"))
                .build();
        assertThat(new PositionWeightSumRule().evaluate(ctx(f))).isEmpty();
    }

    @Test
    void positionWeightWithNoCellsSkipsRule() {
        TptFile f = new TestFileBuilder()
                .row(values("12", "FR12"))     // no field 26 anywhere
                .build();
        assertThat(new PositionWeightSumRule().evaluate(ctx(f))).isEmpty();
    }

    // -------------------------------------------------- XF-06 NavConsistency

    @Test
    void navMatchingPriceTimesSharesIsClean() {
        TptFile f = new TestFileBuilder()
                .row(values("5", "10000000", "8", "100", "8b", "100000"))
                .build();
        assertThat(new NavConsistencyRule().evaluate(ctx(f))).isEmpty();
    }

    @Test
    void navJustWithinOnePercentToleranceIsClean() {
        // 100 * 100000 = 10_000_000; 1 % above = 10_100_000 — should still pass.
        TptFile f = new TestFileBuilder()
                .row(values("5", "10000000", "8", "101", "8b", "100000"))
                .build();
        // Exactly at boundary — relative diff = 0.01, so the rule treats it as a clean fit
        // (the inequality is strict in the implementation: > tolerance).
        assertThat(new NavConsistencyRule().evaluate(ctx(f))).isEmpty();
    }

    @Test
    void navWayOffIsFlagged() {
        TptFile f = new TestFileBuilder()
                .row(values("5", "10000000", "8", "150", "8b", "100000"))
                .build();
        assertThat(new NavConsistencyRule().evaluate(ctx(f))).hasSize(1);
    }

    @Test
    void navWithMissingFieldsIsSilent() {
        TptFile f = new TestFileBuilder()
                .row(values("5", "10000000"))   // missing 8 and 8b
                .build();
        assertThat(new NavConsistencyRule().evaluate(ctx(f))).isEmpty();
    }

    @Test
    void navWithZeroTotalIsSilent() {
        TptFile f = new TestFileBuilder()
                .row(values("5", "0", "8", "100", "8b", "100000"))
                .build();
        assertThat(new NavConsistencyRule().evaluate(ctx(f))).isEmpty();
    }

    // -------------------------------------------------- XF-05 CashPercentage

    @Test
    void cashRatioMatchesComputedSumIsClean() {
        TptFile f = new TestFileBuilder()
                .row(values("5", "10000000", "9", "0.20", "12", "FR71", "24", "2000000"))
                .row(values("12", "FR12", "24", "5000000"))
                .build();
        assertThat(new CashPercentageRule().evaluate(ctx(f))).isEmpty();
    }

    @Test
    void cashRatioOffByMoreThan5PercentIsFlagged() {
        TptFile f = new TestFileBuilder()
                .row(values("5", "10000000", "9", "0.50", "12", "FR71", "24", "2000000"))
                .build();
        assertThat(new CashPercentageRule().evaluate(ctx(f))).hasSize(1);
    }

    @Test
    void cashRatioWithoutTotalNetAssetsIsSilent() {
        TptFile f = new TestFileBuilder()
                .row(values("9", "0.20", "12", "FR71", "24", "2000000"))
                .build();
        assertThat(new CashPercentageRule().evaluate(ctx(f))).isEmpty();
    }

    @Test
    void cashRatioWithoutDeclaredRatioIsSilent() {
        TptFile f = new TestFileBuilder()
                .row(values("5", "10000000", "12", "FR71", "24", "2000000"))
                .build();
        assertThat(new CashPercentageRule().evaluate(ctx(f))).isEmpty();
    }

    // --------------------------------------------------- XF-12 DateOrder

    @Test
    void reportingEqualToValuationIsClean() {
        TptFile f = new TestFileBuilder()
                .row(values("6", "2025-12-31", "7", "2025-12-31"))
                .build();
        assertThat(new DateOrderRule().evaluate(ctx(f))).isEmpty();
    }

    @Test
    void reportingBeforeValuationIsError() {
        TptFile f = new TestFileBuilder()
                .row(values("6", "2025-12-31", "7", "2025-11-30"))
                .build();
        List<Finding> findings = new DateOrderRule().evaluate(ctx(f));
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void dateOrderWithMalformedDatesIsSilent() {
        TptFile f = new TestFileBuilder()
                .row(values("6", "31/12/2025", "7", "30/11/2025"))
                .build();
        assertThat(new DateOrderRule().evaluate(ctx(f))).isEmpty();
    }

    // ----------------------------------------- XF-11 MaturityAfterReporting

    @Test
    void maturityFutureIsClean() {
        TptFile f = new TestFileBuilder()
                .row(values("7", "2025-12-31", "12", "FR12", "39", "2030-12-31"))
                .build();
        assertThat(new MaturityAfterReportingRule().evaluate(ctx(f))).isEmpty();
    }

    @Test
    void maturityPastIsWarning() {
        TptFile f = new TestFileBuilder()
                .row(values("7", "2025-12-31", "12", "FR12", "39", "2020-01-01"))
                .build();
        List<Finding> findings = new MaturityAfterReportingRule().evaluate(ctx(f));
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void maturityForNonRelevantCicSkipped() {
        TptFile f = new TestFileBuilder()
                .row(values("7", "2025-12-31", "12", "DE31", "39", "2020-01-01"))   // CIC 3 = equity
                .build();
        assertThat(new MaturityAfterReportingRule().evaluate(ctx(f))).isEmpty();
    }

    @Test
    void maturityWithoutReportingDateIsSilent() {
        TptFile f = new TestFileBuilder()
                .row(values("12", "FR12", "39", "2020-01-01"))   // no field 7
                .build();
        assertThat(new MaturityAfterReportingRule().evaluate(ctx(f))).isEmpty();
    }

    // ---------------------------------------- combined: empty file edge case

    @Test
    void allRulesNoOpOnEmptyFile() {
        TptFile f = new TestFileBuilder().build();
        assertThat(new PositionWeightSumRule().evaluate(ctx(f))).isEmpty();
        assertThat(new CashPercentageRule().evaluate(ctx(f))).isEmpty();
        assertThat(new NavConsistencyRule().evaluate(ctx(f))).isEmpty();
        assertThat(new DateOrderRule().evaluate(ctx(f))).isEmpty();
        assertThat(new MaturityAfterReportingRule().evaluate(ctx(f))).isEmpty();
    }
}
