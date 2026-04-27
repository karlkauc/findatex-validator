package com.findatex.validator.validation;

import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.validation.rules.ConditionalPresenceRule;
import com.findatex.validator.validation.rules.PresenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalFieldPresenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuleRegistryTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();
    private static final Set<ProfileKey> ALL = new java.util.HashSet<>(java.util.Arrays.asList(TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB, TptProfiles.NW_675, TptProfiles.SST));

    @Test
    void everyConditionalRequirementProducesARule() {
        List<Rule> rules = RuleRegistry.build(CATALOG, ALL);
        for (ConditionalRequirement req : RuleRegistry.CONDITIONAL_REQUIREMENTS) {
            assertThat(rules)
                    .as("rule registered for %s", req.ruleId())
                    .anyMatch(r -> r instanceof ConditionalFieldPresenceRule cf
                            && cf.requirement().ruleId().equals(req.ruleId()));
        }
    }

    @Test
    void targetsCoveredByXfRulesHaveNoGenericPresenceRule() {
        List<Rule> rules = RuleRegistry.build(CATALOG, ALL);
        // Declarative XF targets (CONDITIONAL_REQUIREMENTS) +
        //   33, 34 (XF-10 InterestRateTypeRule),
        //   67    (XF-14 UnderlyingCicRule),
        //   95    (narrative look-through condition — cannot be auto-checked).
        Set<String> handled = Set.of(
                "31", "33", "34", "35", "36", "37", "43", "44", "45",
                "47", "50", "67", "84", "95", "115", "119", "139");

        for (Rule r : rules) {
            if (r instanceof PresenceRule pr) {
                String fnum = pr.id().split("/")[1];
                assertThat(handled)
                        .as("PresenceRule for handled field %s should be suppressed (id=%s)", fnum, pr.id())
                        .doesNotContain(fnum);
            }
            if (r instanceof ConditionalPresenceRule cr) {
                String fnum = cr.id().split("/")[1];
                assertThat(handled)
                        .as("ConditionalPresenceRule for handled field %s should be suppressed (id=%s)", fnum, cr.id())
                        .doesNotContain(fnum);
            }
        }
    }

    @Test
    void otherFieldsStillGetGenericRules() {
        List<Rule> rules = RuleRegistry.build(CATALOG, ALL);

        // Field 12 (CIC) is M for SOLVENCY_II — must still get a PresenceRule.
        assertThat(rules)
                .as("field 12 must keep its generic PresenceRule")
                .anyMatch(r -> r instanceof PresenceRule pr && pr.id().contains("/12/"));

        // Field 13 (Economic_zone) is C for SOLVENCY_II — must still get a ConditionalPresenceRule.
        assertThat(rules)
                .as("field 13 must keep its generic ConditionalPresenceRule")
                .anyMatch(r -> r instanceof ConditionalPresenceRule cr && cr.id().contains("/13/"));
    }

    @Test
    void allConditionalTargetFieldsAreRecognisedInTheCatalog() {
        // Sanity check: every targetFieldNum referenced by a requirement must exist in the spec.
        for (ConditionalRequirement req : RuleRegistry.CONDITIONAL_REQUIREMENTS) {
            assertThat(CATALOG.byNumKey(req.targetFieldNum()))
                    .as("target field %s of %s exists in catalog", req.targetFieldNum(), req.ruleId())
                    .isPresent();
        }
    }
}
