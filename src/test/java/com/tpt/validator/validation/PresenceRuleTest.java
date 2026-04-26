package com.tpt.validator.validation;

import com.tpt.validator.domain.TptFile;
import com.tpt.validator.spec.FieldSpec;
import com.tpt.validator.spec.Profile;
import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.spec.SpecLoader;
import com.tpt.validator.validation.rules.ConditionalPresenceRule;
import com.tpt.validator.validation.rules.PresenceRule;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.tpt.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

class PresenceRuleTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    private ValidationContext ctx(TptFile f, Set<Profile> profiles) {
        return new ValidationContext(f, CATALOG, profiles);
    }

    @Test
    void presenceRuleSkipsRowsOutsideCicScope() {
        // Field 13 (Economic_zone_of_the_quotation_place) only applies to CIC 3.
        FieldSpec spec = CATALOG.byNumKey("13").orElseThrow();
        PresenceRule rule = new PresenceRule(spec, Profile.SOLVENCY_II);

        TptFile equityRow = new TestFileBuilder()
                .row(values("12", "DE31"))                // CIC 3 → applicable, but field 13 is C not M
                .build();
        // Field 13 is conditional, not mandatory — PresenceRule shouldn't fire even on CIC 3.
        // We pick a different spec to test the scope-skip logic: use field 33 (Coupon rate, M for SCR).
        // But for this test purpose: confirm a non-applicable CIC row produces no finding.
        FieldSpec field32 = CATALOG.byNumKey("32").orElseThrow();   // Interest_rate_type — applies to bonds
        if (field32.flag(Profile.SOLVENCY_II) == com.tpt.validator.spec.Flag.M) {
            PresenceRule r2 = new PresenceRule(field32, Profile.SOLVENCY_II);
            // Equity row → CIC 3 → field 32 is not in CIC 3 → presence rule must skip.
            assertThat(r2.evaluate(ctx(equityRow, EnumSet.of(Profile.SOLVENCY_II)))).isEmpty();
        }
        // The unused 'rule' silences IDE warnings.
        assertThat(rule.id()).startsWith("PRESENCE/");
    }

    @Test
    void presenceRuleSilentWhenProfileNotActive() {
        FieldSpec spec = CATALOG.byNumKey("12").orElseThrow();   // CIC code, M for SOLVENCY_II
        PresenceRule rule = new PresenceRule(spec, Profile.SOLVENCY_II);

        TptFile f = new TestFileBuilder().row(values("17", "Bond")).build();   // missing 12
        assertThat(rule.evaluate(ctx(f, EnumSet.of(Profile.NW_675)))).isEmpty();
        // Active profile includes SOLVENCY_II → fires.
        assertThat(rule.evaluate(ctx(f, EnumSet.of(Profile.SOLVENCY_II)))).isNotEmpty();
    }

    @Test
    void conditionalPresenceRuleSkipsWhenCicMissing() {
        FieldSpec field13 = CATALOG.byNumKey("13").orElseThrow();   // Economic zone, applies to CIC 3 only
        ConditionalPresenceRule rule = new ConditionalPresenceRule(field13, Profile.SOLVENCY_II);

        TptFile noCic = new TestFileBuilder().row(values("17", "Anonymous")).build();
        // Without a parseable CIC, conditional rule cannot enforce — must be silent.
        assertThat(rule.evaluate(ctx(noCic, EnumSet.of(Profile.SOLVENCY_II)))).isEmpty();
    }

    @Test
    void conditionalPresenceRuleFiresWhenCicMatchesAndValueMissing() {
        FieldSpec field13 = CATALOG.byNumKey("13").orElseThrow();
        ConditionalPresenceRule rule = new ConditionalPresenceRule(field13, Profile.SOLVENCY_II);

        TptFile equity = new TestFileBuilder().row(values("12", "DE31")).build();   // CIC 3, field 13 missing
        List<Finding> findings = rule.evaluate(ctx(equity, EnumSet.of(Profile.SOLVENCY_II)));
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(findings.get(0).fieldNum()).isEqualTo("13");
    }

    @Test
    void conditionalPresenceRuleSkipsForFieldsAppliesToAllCic() {
        // Field 12 (CIC code) applies to every CIC and is M, not C — but we just verify the rule
        // short-circuits when appliesToAllCic() is true.
        FieldSpec field12 = CATALOG.byNumKey("12").orElseThrow();
        ConditionalPresenceRule rule = new ConditionalPresenceRule(field12, Profile.SOLVENCY_II);
        TptFile f = new TestFileBuilder().row(values("17", "x")).build();
        assertThat(rule.evaluate(ctx(f, EnumSet.of(Profile.SOLVENCY_II)))).isEmpty();
    }
}
