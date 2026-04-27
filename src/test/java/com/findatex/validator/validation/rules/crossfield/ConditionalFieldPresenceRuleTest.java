package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.TestFileBuilder;
import com.findatex.validator.validation.ValidationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.findatex.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

class ConditionalFieldPresenceRuleTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    private ValidationContext ctx(TptFile file) {
        return new ValidationContext(file, CATALOG, new java.util.HashSet<>(java.util.Arrays.asList(TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB, TptProfiles.NW_675, TptProfiles.SST)));
    }

    /** Helper that builds the rule directly from a {@link ConditionalRequirement}. */
    private static ConditionalFieldPresenceRule rule(ConditionalRequirement req) {
        return new ConditionalFieldPresenceRule(req);
    }

    // ---------------------------------------------------------- XF-20 (47 / 48)

    @Test
    void xf20_triggeredWithMissingTargetEmitsError() {
        ConditionalRequirement req = new ConditionalRequirement(
                "XF-20/ISSUER_LEI_PRESENT", "48", FieldPredicate.EqualsAny.of("1"),
                "47", Severity.ERROR);

        TptFile file = new TestFileBuilder()
                .row(values("12", "FR12", "48", "1"))   // type = LEI but code missing
                .build();

        List<Finding> findings = rule(req).evaluate(ctx(file));
        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.severity()).isEqualTo(Severity.ERROR);
        assertThat(f.ruleId()).isEqualTo("XF-20/ISSUER_LEI_PRESENT");
        assertThat(f.fieldNum()).isEqualTo("47");
        assertThat(f.message()).contains("47").contains("48").contains("\"1\"");
    }

    @Test
    void xf20_triggeredWithTargetPresentEmitsNothing() {
        ConditionalRequirement req = new ConditionalRequirement(
                "XF-20/ISSUER_LEI_PRESENT", "48", FieldPredicate.EqualsAny.of("1"),
                "47", Severity.ERROR);

        TptFile file = new TestFileBuilder()
                .row(values("12", "FR12", "48", "1", "47", "529900D6BF99LW9R2E68"))
                .build();
        assertThat(rule(req).evaluate(ctx(file))).isEmpty();
    }

    @Test
    void xf20_notTriggeredWhenSourceIsBlankOrDifferent() {
        ConditionalRequirement req = new ConditionalRequirement(
                "XF-20/ISSUER_LEI_PRESENT", "48", FieldPredicate.EqualsAny.of("1"),
                "47", Severity.ERROR);

        TptFile blankSource = new TestFileBuilder()
                .row(values("12", "FR12"))                          // 48 unset → no warning
                .build();
        assertThat(rule(req).evaluate(ctx(blankSource))).isEmpty();

        TptFile differentSource = new TestFileBuilder()
                .row(values("12", "FR12", "48", "9"))               // type=None
                .build();
        assertThat(rule(req).evaluate(ctx(differentSource))).isEmpty();
    }

    // ---------------------------------------------------------- XF-25 (138 / 139)

    @Test
    void xf25_collateralValueRequiredForEligibleAssets() {
        ConditionalRequirement req = new ConditionalRequirement(
                "XF-25/COLLATERAL_VALUE", "138", FieldPredicate.EqualsAny.of("1", "2", "3"),
                "139", Severity.ERROR);

        for (String triggerVal : new String[]{"1", "2", "3"}) {
            TptFile file = new TestFileBuilder()
                    .row(values("12", "FR22", "138", triggerVal))
                    .build();
            List<Finding> findings = rule(req).evaluate(ctx(file));
            assertThat(findings).as("trigger=%s", triggerVal).hasSize(1);
            assertThat(findings.get(0).fieldNum()).isEqualTo("139");
        }

        for (String nonTrigger : new String[]{"0", "4", "9"}) {
            TptFile file = new TestFileBuilder()
                    .row(values("12", "FR22", "138", nonTrigger))
                    .build();
            assertThat(rule(req).evaluate(ctx(file))).as("trigger=%s", nonTrigger).isEmpty();
        }
    }

    // ---------------------------------------------------------- XF-18 (42 ⇒ 43, 44)

    @Test
    void xf18_callPutTriggersDateAndDirection() {
        ConditionalRequirement dateReq = new ConditionalRequirement(
                "XF-18/CALL_PUT_DATE", "42", FieldPredicate.EqualsAny.of("Cal", "Put"),
                "43", Severity.ERROR);
        ConditionalRequirement dirReq = new ConditionalRequirement(
                "XF-18/OPTION_DIRECTION", "42", FieldPredicate.EqualsAny.of("Cal", "Put"),
                "44", Severity.ERROR);

        TptFile cal = new TestFileBuilder().row(values("12", "FR22", "42", "Cal")).build();
        assertThat(rule(dateReq).evaluate(ctx(cal))).hasSize(1);
        assertThat(rule(dirReq).evaluate(ctx(cal))).hasSize(1);

        TptFile put = new TestFileBuilder().row(values("12", "FR22", "42", "Put")).build();
        assertThat(rule(dateReq).evaluate(ctx(put))).hasSize(1);

        TptFile cap = new TestFileBuilder().row(values("12", "FR22", "42", "Cap")).build();
        assertThat(rule(dateReq).evaluate(ctx(cap))).isEmpty();

        TptFile flr = new TestFileBuilder().row(values("12", "FR22", "42", "Flr")).build();
        assertThat(rule(dateReq).evaluate(ctx(flr))).isEmpty();
    }

    // ---------------------------------------------------------- XF-17 (34 ⇒ 35,36,37)

    @Test
    void xf17_indexCodeTriggersThreeRequirementsIndependently() {
        ConditionalRequirement type = new ConditionalRequirement(
                "XF-17/INDEX_TYPE", "34", FieldPredicate.NotBlank.INSTANCE,
                "35", Severity.ERROR);
        ConditionalRequirement name = new ConditionalRequirement(
                "XF-17/INDEX_NAME", "34", FieldPredicate.NotBlank.INSTANCE,
                "36", Severity.ERROR);
        ConditionalRequirement margin = new ConditionalRequirement(
                "XF-17/INDEX_MARGIN", "34", FieldPredicate.NotBlank.INSTANCE,
                "37", Severity.ERROR);

        TptFile file = new TestFileBuilder()
                .row(values("12", "FR22", "34", "EUR003M"))   // index ID set, all three companions missing
                .build();

        assertThat(rule(type).evaluate(ctx(file))).hasSize(1);
        assertThat(rule(name).evaluate(ctx(file))).hasSize(1);
        assertThat(rule(margin).evaluate(ctx(file))).hasSize(1);
    }

    // ---------------------------------------------------------- Multi-row matrix

    @Test
    void evaluatesEveryRowIndependently() {
        ConditionalRequirement req = new ConditionalRequirement(
                "XF-20/ISSUER_LEI_PRESENT", "48", FieldPredicate.EqualsAny.of("1"),
                "47", Severity.ERROR);

        TptFile file = new TestFileBuilder()
                .row(values("12", "FR12", "48", "1"))                               // missing → finding
                .row(values("12", "DE31", "48", "1", "47", "529900D6BF99LW9R2E68")) // present → ok
                .row(values("12", "XL71", "48", "9"))                               // not triggered → ok
                .row(values("12", "DE22", "48", "1"))                               // missing → finding
                .build();

        List<Finding> findings = rule(req).evaluate(ctx(file));
        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(Finding::rowIndex).containsExactly(1, 4);
    }

    // ---------------------------------------------------------- Severity routing

    @Test
    void warningSeverityRouted() {
        ConditionalRequirement req = new ConditionalRequirement(
                "XF-DEMO/WARN", "48", FieldPredicate.EqualsAny.of("1"),
                "47", Severity.WARNING);
        TptFile file = new TestFileBuilder().row(values("12", "FR12", "48", "1")).build();
        Finding f = rule(req).evaluate(ctx(file)).get(0);
        assertThat(f.severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void infoSeverityRouted() {
        ConditionalRequirement req = new ConditionalRequirement(
                "XF-DEMO/INFO", "48", FieldPredicate.EqualsAny.of("1"),
                "47", Severity.INFO);
        TptFile file = new TestFileBuilder().row(values("12", "FR12", "48", "1")).build();
        Finding f = rule(req).evaluate(ctx(file)).get(0);
        assertThat(f.severity()).isEqualTo(Severity.INFO);
    }
}
