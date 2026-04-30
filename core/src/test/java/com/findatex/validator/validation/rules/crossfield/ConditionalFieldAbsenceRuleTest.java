package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.api.ProfileKey;
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

class ConditionalFieldAbsenceRuleTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    private ValidationContext ctx(TptFile file) {
        Set<ProfileKey> profiles = new HashSet<>(List.of(
                TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB,
                TptProfiles.NW_675, TptProfiles.SST));
        return new ValidationContext(file, CATALOG, profiles);
    }

    private static ConditionalFieldAbsenceRule rule(ConditionalRequirement req) {
        return new ConditionalFieldAbsenceRule(req);
    }

    @Test
    void triggeredWithTargetPresentEmitsWarning() {
        ConditionalRequirement req = new ConditionalRequirement(
                "EET-XF-ART30-MUST-BE-ABSENT", "27", FieldPredicate.EqualsAny.of("0"),
                "30", Severity.WARNING);

        TptFile file = new TestFileBuilder()
                .row(values("12", "FR12", "27", "0", "30", "0.5"))
                .build();

        List<Finding> findings = rule(req).evaluate(ctx(file));
        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.severity()).isEqualTo(Severity.WARNING);
        assertThat(f.ruleId()).isEqualTo("EET-XF-ART30-MUST-BE-ABSENT");
        assertThat(f.fieldNum()).isEqualTo("30");
        assertThat(f.message()).contains("must be empty").contains("27").contains("\"0\"");
    }

    @Test
    void triggeredWithTargetAbsentEmitsNothing() {
        ConditionalRequirement req = new ConditionalRequirement(
                "EET-XF-ART30-MUST-BE-ABSENT", "27", FieldPredicate.EqualsAny.of("0"),
                "30", Severity.WARNING);

        TptFile file = new TestFileBuilder()
                .row(values("12", "FR12", "27", "0"))
                .build();

        assertThat(rule(req).evaluate(ctx(file))).isEmpty();
    }

    @Test
    void notTriggeredWhenSourceIsBlankOrDifferent() {
        ConditionalRequirement req = new ConditionalRequirement(
                "EET-XF-ART30-MUST-BE-ABSENT", "27", FieldPredicate.EqualsAny.of("0"),
                "30", Severity.WARNING);

        TptFile blankSource = new TestFileBuilder()
                .row(values("12", "FR12", "30", "0.5"))
                .build();
        assertThat(rule(req).evaluate(ctx(blankSource))).isEmpty();

        TptFile art8 = new TestFileBuilder()
                .row(values("12", "FR12", "27", "8", "30", "0.5"))
                .build();
        assertThat(rule(req).evaluate(ctx(art8))).isEmpty();
    }

    @Test
    void evaluatesEveryRowIndependently() {
        ConditionalRequirement req = new ConditionalRequirement(
                "EET-XF-ART30-MUST-BE-ABSENT", "27", FieldPredicate.EqualsAny.of("0"),
                "30", Severity.WARNING);

        TptFile file = new TestFileBuilder()
                .row(values("12", "A", "27", "0", "30", "0.5"))      // out-of-scope w/ Art-8 field → finding
                .row(values("12", "B", "27", "8", "30", "0.5"))      // legit Art-8 → ok
                .row(values("12", "C", "27", "0"))                   // out-of-scope, no Art-8 → ok
                .row(values("12", "D", "27", "0", "30", "0.0"))      // out-of-scope w/ Art-8 (even "0.0") → finding
                .build();

        List<Finding> findings = rule(req).evaluate(ctx(file));
        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(Finding::rowIndex).containsExactly(1, 4);
    }

    @Test
    void severityErrorRouted() {
        ConditionalRequirement req = new ConditionalRequirement(
                "X", "27", FieldPredicate.EqualsAny.of("0"), "30", Severity.ERROR);
        TptFile file = new TestFileBuilder().row(values("27", "0", "30", "x")).build();
        Finding f = rule(req).evaluate(ctx(file)).get(0);
        assertThat(f.severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void severityInfoRouted() {
        ConditionalRequirement req = new ConditionalRequirement(
                "X", "27", FieldPredicate.EqualsAny.of("0"), "30", Severity.INFO);
        TptFile file = new TestFileBuilder().row(values("27", "0", "30", "x")).build();
        Finding f = rule(req).evaluate(ctx(file)).get(0);
        assertThat(f.severity()).isEqualTo(Severity.INFO);
    }
}
