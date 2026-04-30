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

class ConditionalAnyFieldPresenceRuleTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    private ValidationContext ctx(TptFile file) {
        Set<ProfileKey> profiles = new HashSet<>(List.of(
                TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB,
                TptProfiles.NW_675, TptProfiles.SST));
        return new ValidationContext(file, CATALOG, profiles);
    }

    private static ConditionalAnyFieldPresenceRule rule(ConditionalAnyOfRequirement req) {
        return new ConditionalAnyFieldPresenceRule(req);
    }

    @Test
    void allTargetsBlankWhilePredicateHoldsEmitsOneFinding() {
        ConditionalAnyOfRequirement req = new ConditionalAnyOfRequirement(
                "EET-XF-ART8-MIN-SI-SPLIT", "41", FieldPredicate.NotBlank.INSTANCE,
                List.of("42", "43", "44"), Severity.WARNING);

        TptFile file = new TestFileBuilder()
                .row(values("41", "0.3"))
                .build();

        List<Finding> findings = rule(req).evaluate(ctx(file));
        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.severity()).isEqualTo(Severity.WARNING);
        assertThat(f.fieldNum()).isEqualTo("42");
        assertThat(f.message()).contains("[42, 43, 44]").contains("41");
    }

    @Test
    void anyTargetPresentSatisfiesRule() {
        ConditionalAnyOfRequirement req = new ConditionalAnyOfRequirement(
                "X", "41", FieldPredicate.NotBlank.INSTANCE,
                List.of("42", "43", "44"), Severity.WARNING);

        for (String present : new String[]{"42", "43", "44"}) {
            TptFile file = new TestFileBuilder()
                    .row(values("41", "0.3", present, "Y"))
                    .build();
            assertThat(rule(req).evaluate(ctx(file)))
                    .as("present=%s", present)
                    .isEmpty();
        }
    }

    @Test
    void allTargetsPresentSatisfiesRule() {
        ConditionalAnyOfRequirement req = new ConditionalAnyOfRequirement(
                "X", "41", FieldPredicate.NotBlank.INSTANCE,
                List.of("42", "43", "44"), Severity.WARNING);

        TptFile file = new TestFileBuilder()
                .row(values("41", "0.3", "42", "Y", "43", "Y", "44", "Y"))
                .build();
        assertThat(rule(req).evaluate(ctx(file))).isEmpty();
    }

    @Test
    void predicateFalseEmitsNothing() {
        ConditionalAnyOfRequirement req = new ConditionalAnyOfRequirement(
                "X", "41", FieldPredicate.NotBlank.INSTANCE,
                List.of("42", "43", "44"), Severity.WARNING);

        TptFile blank = new TestFileBuilder()
                .row(values("12", "FR12"))
                .build();
        assertThat(rule(req).evaluate(ctx(blank))).isEmpty();
    }

    @Test
    void evaluatesEveryRowIndependently() {
        ConditionalAnyOfRequirement req = new ConditionalAnyOfRequirement(
                "X", "41", FieldPredicate.NotBlank.INSTANCE,
                List.of("42", "43", "44"), Severity.ERROR);

        TptFile file = new TestFileBuilder()
                .row(values("41", "0.3"))                         // unattributed → finding
                .row(values("41", "0.5", "44", "Y"))              // attributed → ok
                .row(values("12", "x"))                           // predicate false → ok
                .row(values("41", "0.2"))                         // unattributed → finding
                .build();

        List<Finding> findings = rule(req).evaluate(ctx(file));
        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(Finding::severity).containsOnly(Severity.ERROR);
        assertThat(findings).extracting(Finding::rowIndex).containsExactly(1, 4);
    }
}
