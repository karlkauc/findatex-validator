package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.TestFileBuilder;
import com.findatex.validator.validation.ValidationContext;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.findatex.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

class UnderlyingCicRuleTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();
    private static final FieldSpec FIELD_67 = CATALOG.byNumKey("67").orElseThrow();
    private static final UnderlyingCicRule RULE = new UnderlyingCicRule(FIELD_67);

    private ValidationContext ctx(TptFile file) {
        return new ValidationContext(file, CATALOG, new HashSet<>(Set.of(
                TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB,
                TptProfiles.NW_675, TptProfiles.SST)));
    }

    /** CIC 22 (convertible / corporate bond with embedded option) — field 67 mandatory. */
    @Test
    void cic22_missingUnderlyingFlagged() {
        TptFile file = new TestFileBuilder().row(values("12", "BE22")).build();
        List<Finding> findings = RULE.evaluate(ctx(file));
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).fieldNum()).isEqualTo("67");
        assertThat(findings.get(0).message()).contains("22");
    }

    /** CIC 21 (plain corporate bond) — field 67 NOT mandatory; spec qualifier is "x for 22". */
    @Test
    void cic21_missingUnderlyingNotFlagged() {
        TptFile file = new TestFileBuilder().row(values("12", "BE21")).build();
        assertThat(RULE.evaluate(ctx(file))).isEmpty();
    }

    /** CIC 23..29 also corporate bonds, also exempt — only "22" is in scope. */
    @Test
    void otherCic2Subcodes_notFlagged() {
        for (String sub : new String[]{"1", "3", "4", "5", "6", "7", "8", "9"}) {
            TptFile file = new TestFileBuilder().row(values("12", "BE2" + sub)).build();
            assertThat(RULE.evaluate(ctx(file))).as("CIC 2%s", sub).isEmpty();
        }
    }

    /** CIC A (futures) — field 67 mandatory for all sub-codes. */
    @Test
    void cicA_anySubcode_flagged() {
        TptFile file = new TestFileBuilder().row(values("12", "XLA1")).build();
        assertThat(RULE.evaluate(ctx(file))).hasSize(1);
    }

    /** CIC B (call options) — mandatory unconditionally. */
    @Test
    void cicB_anySubcode_flagged() {
        TptFile file = new TestFileBuilder().row(values("12", "XLB7")).build();
        assertThat(RULE.evaluate(ctx(file))).hasSize(1);
    }

    /** CIC C (put options) — mandatory unconditionally. */
    @Test
    void cicC_anySubcode_flagged() {
        TptFile file = new TestFileBuilder().row(values("12", "XLC2")).build();
        assertThat(RULE.evaluate(ctx(file))).hasSize(1);
    }

    /** CIC D4 / D5 (cross-currency / total return swaps) — mandatory. */
    @Test
    void cicD4_andD5_flagged() {
        TptFile d4 = new TestFileBuilder().row(values("12", "XLD4")).build();
        TptFile d5 = new TestFileBuilder().row(values("12", "XLD5")).build();
        assertThat(RULE.evaluate(ctx(d4))).hasSize(1);
        assertThat(RULE.evaluate(ctx(d5))).hasSize(1);
    }

    /** CIC D1, D2, D3, D6, D7 — out of scope ("x for D4, D5"). */
    @Test
    void otherCicDSubcodes_notFlagged() {
        for (String sub : new String[]{"1", "2", "3", "6", "7", "8", "9"}) {
            TptFile file = new TestFileBuilder().row(values("12", "XLD" + sub)).build();
            assertThat(RULE.evaluate(ctx(file))).as("CIC D%s", sub).isEmpty();
        }
    }

    /** CIC F (credit derivatives) — mandatory unconditionally. */
    @Test
    void cicF_anySubcode_flagged() {
        TptFile file = new TestFileBuilder().row(values("12", "XLF1")).build();
        assertThat(RULE.evaluate(ctx(file))).hasSize(1);
    }

    /** Out-of-scope CIC families (1, 3, 4, 5, 6, 7, 8, 9, 0, E) — never flagged. */
    @Test
    void outOfScopeCicFamilies_neverFlagged() {
        for (String code : new String[]{"FR11", "DE31", "LU41", "XL55", "BE61", "FR71", "DE81", "LU91", "BE01", "XLE1"}) {
            TptFile file = new TestFileBuilder().row(values("12", code)).build();
            assertThat(RULE.evaluate(ctx(file))).as("CIC %s", code).isEmpty();
        }
    }

    /** Field 67 populated → no finding even when scope applies. */
    @Test
    void underlyingPresent_noFinding() {
        TptFile file = new TestFileBuilder()
                .row(values("12", "BE22", "67", "FR31"))
                .build();
        assertThat(RULE.evaluate(ctx(file))).isEmpty();
    }

    /** Multi-row mix: only the in-scope row with empty 67 yields a finding. */
    @Test
    void multiRow_independentEvaluation() {
        TptFile file = new TestFileBuilder()
                .row(values("12", "BE21"))                       // out of scope (CIC 21)
                .row(values("12", "BE22"))                       // in scope, field 67 missing → finding
                .row(values("12", "XLA1", "67", "DE31"))         // in scope but populated
                .row(values("12", "XLD1"))                       // CIC D1, out of scope
                .row(values("12", "XLD4"))                       // CIC D4, in scope, missing → finding
                .build();
        List<Finding> findings = RULE.evaluate(ctx(file));
        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(Finding::rowIndex).containsExactly(2, 5);
    }

    /** Unparseable CIC — rule must skip the row gracefully. */
    @Test
    void unparseableCic_skipsRow() {
        TptFile file = new TestFileBuilder().row(values("12", "??")).build();
        assertThat(RULE.evaluate(ctx(file))).isEmpty();
    }
}
