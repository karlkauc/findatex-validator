package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.TestFileBuilder;
import com.findatex.validator.validation.ValidationContext;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.findatex.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-CIC-Scope-Tests für XF-11 / MaturityAfterReportingRule. Die Regel feuert
 * eine WARNING, wenn Feld 39 (Maturity) vor Feld 7 (Reporting) liegt — und zwar
 * auf jeder Zeile, die im CIC-Anwendbarkeitsbereich von Feld 39 liegt
 * (Stand TPT V7: CIC 1, 2, 5, 6, 7 nur Sub 3/4/5, 8, A, B, C, D, E, F).
 *
 * Vor Stufe 2 war die Whitelist hartkodiert auf {1, 2, 5, 6, 8} und verpasste
 * deshalb Maturity-Past-Errors auf CIC7 (Money market), Futures, Calls, Puts,
 * Swaps, Forwards und Credit Derivatives.
 */
class MaturityAfterReportingRuleCicScopeTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();
    private static final FieldSpec FIELD_39 = CATALOG.byNumKey("39").orElseThrow();
    private static final MaturityAfterReportingRule RULE = new MaturityAfterReportingRule(FIELD_39);

    private ValidationContext ctx(TptFile file) {
        return new ValidationContext(file, CATALOG, new HashSet<>(Set.of(
                TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB,
                TptProfiles.NW_675, TptProfiles.SST)));
    }

    /** Helper: ein Row-Builder mit Reporting=2025-12-31 und beliebiger CIC + Maturity. */
    private TptFile single(String cic, String maturity) {
        return new TestFileBuilder()
                .row(values("7", "2025-12-31", "12", cic, "39", maturity))
                .build();
    }

    // ---------------------------------------------------------- in-scope CIC families

    @Test
    void cic1_governmentBond_pastMaturityFlagged() {
        List<Finding> f = RULE.evaluate(ctx(single("FR12", "2020-01-01")));
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(f.get(0).fieldNum()).isEqualTo("39");
    }

    @Test
    void cic2_corporateBond_pastMaturityFlagged() {
        assertThat(RULE.evaluate(ctx(single("BE21", "2020-01-01")))).hasSize(1);
    }

    @Test
    void cic5_structuredNote_pastMaturityFlagged() {
        assertThat(RULE.evaluate(ctx(single("XL51", "2020-01-01")))).hasSize(1);
    }

    @Test
    void cic6_collateralizedSecurity_pastMaturityFlagged() {
        assertThat(RULE.evaluate(ctx(single("XL61", "2020-01-01")))).hasSize(1);
    }

    @Test
    void cic8_mortgageOrLoan_pastMaturityFlagged() {
        assertThat(RULE.evaluate(ctx(single("DE81", "2020-01-01")))).hasSize(1);
    }

    /** CIC 7 ist eingeschränkt auf Sub-Codes 3/4/5 (Money market). */
    @Test
    void cic7_moneyMarketSubcodes345_pastMaturityFlagged() {
        for (String sub : new String[]{"3", "4", "5"}) {
            assertThat(RULE.evaluate(ctx(single("FR7" + sub, "2020-01-01"))))
                    .as("CIC 7%s", sub).hasSize(1);
        }
    }

    /** CIC 7 Sub-Codes 1, 2 (Cash on account / on deposit) sind out-of-scope. */
    @Test
    void cic7_pureCashSubcodes_notFlagged() {
        for (String sub : new String[]{"1", "2"}) {
            assertThat(RULE.evaluate(ctx(single("FR7" + sub, "2020-01-01"))))
                    .as("CIC 7%s", sub).isEmpty();
        }
    }

    @Test
    void cicA_futures_pastMaturityFlagged() {
        // ENTSPRICHT NEUEM VERHALTEN: Vor Stufe 2 wurde dieser Fall ignoriert.
        assertThat(RULE.evaluate(ctx(single("XLA1", "2020-01-01")))).hasSize(1);
    }

    @Test
    void cicB_callOption_pastMaturityFlagged() {
        assertThat(RULE.evaluate(ctx(single("XLB1", "2020-01-01")))).hasSize(1);
    }

    @Test
    void cicC_putOption_pastMaturityFlagged() {
        assertThat(RULE.evaluate(ctx(single("XLC1", "2020-01-01")))).hasSize(1);
    }

    @Test
    void cicD_swap_pastMaturityFlagged() {
        assertThat(RULE.evaluate(ctx(single("XLD1", "2020-01-01")))).hasSize(1);
    }

    @Test
    void cicE_forward_pastMaturityFlagged() {
        assertThat(RULE.evaluate(ctx(single("XLE1", "2020-01-01")))).hasSize(1);
    }

    @Test
    void cicF_creditDerivative_pastMaturityFlagged() {
        assertThat(RULE.evaluate(ctx(single("XLF1", "2020-01-01")))).hasSize(1);
    }

    // ---------------------------------------------------------- out-of-scope CIC families

    /** CIC 0, 3, 4, 9 — Other / Equity / CIU / Property — kein Maturity-Konzept. */
    @Test
    void outOfScopeCicFamilies_neverFlagged() {
        for (String cic : new String[]{"FR01", "DE31", "LU41", "BE91"}) {
            assertThat(RULE.evaluate(ctx(single(cic, "2020-01-01"))))
                    .as("CIC %s", cic).isEmpty();
        }
    }

    // ---------------------------------------------------------- value semantics

    @Test
    void futureMaturity_noFinding() {
        assertThat(RULE.evaluate(ctx(single("FR12", "2030-12-31")))).isEmpty();
    }

    @Test
    void maturityEqualsReporting_noFinding() {
        // Boundary: maturity == reporting → not "before" → no warning.
        assertThat(RULE.evaluate(ctx(single("FR12", "2025-12-31")))).isEmpty();
    }

    @Test
    void noReportingDate_silent() {
        TptFile f = new TestFileBuilder()
                .row(values("12", "FR12", "39", "2020-01-01"))   // no field 7
                .build();
        assertThat(RULE.evaluate(ctx(f))).isEmpty();
    }

    @Test
    void unparsableReportingDate_silent() {
        TptFile f = new TestFileBuilder()
                .row(values("7", "not-a-date", "12", "FR12", "39", "2020-01-01"))
                .build();
        assertThat(RULE.evaluate(ctx(f))).isEmpty();
    }

    @Test
    void unparsableMaturity_skipsRow() {
        TptFile f = new TestFileBuilder()
                .row(values("7", "2025-12-31", "12", "FR12", "39", "garbage"))
                .build();
        assertThat(RULE.evaluate(ctx(f))).isEmpty();
    }

    @Test
    void missingMaturity_skipsRow() {
        TptFile f = new TestFileBuilder()
                .row(values("7", "2025-12-31", "12", "FR12"))   // no field 39
                .build();
        assertThat(RULE.evaluate(ctx(f))).isEmpty();
    }

    // ---------------------------------------------------------- multi-row mix

    @Test
    void multiRow_evaluatesEveryRowIndependently() {
        TptFile f = new TestFileBuilder()
                .row(values("7", "2025-12-31", "12", "FR12", "39", "2020-01-01")) // CIC 1, past → finding
                .row(values("12", "DE31", "39", "2020-01-01"))                    // CIC 3, out of scope
                .row(values("12", "XLA1", "39", "2020-01-01"))                    // CIC A, past → finding (NEU)
                .row(values("12", "FR71"))                                        // CIC 71, no maturity
                .row(values("12", "XLD4", "39", "2030-12-31"))                    // CIC D, future → ok
                .row(values("12", "XLF1", "39", "2019-06-15"))                    // CIC F, past → finding (NEU)
                .build();
        List<Finding> findings = RULE.evaluate(ctx(f));
        assertThat(findings).hasSize(3);
        assertThat(findings).extracting(Finding::rowIndex).containsExactly(1, 3, 6);
    }
}
