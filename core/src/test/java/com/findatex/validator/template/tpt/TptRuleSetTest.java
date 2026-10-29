package com.findatex.validator.template.tpt;

import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.rules.LeiRule;
import com.findatex.validator.validation.rules.NotApplicableRule;
import com.findatex.validator.validation.rules.crossfield.CustodianPairRule;
import com.findatex.validator.validation.rules.crossfield.InterestRateTypeRule;
import com.findatex.validator.validation.rules.ConditionalPresenceRule;
import com.findatex.validator.validation.rules.PresenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalFieldPresenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TptRuleSetTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();
    private static final TptRuleSet RULE_SET = new TptRuleSet();
    private static final Set<ProfileKey> ALL = new java.util.HashSet<>(java.util.Arrays.asList(
            TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB, TptProfiles.NW_675, TptProfiles.SST));

    @Test
    void everyConditionalRequirementProducesARule() {
        List<Rule> rules = RULE_SET.build(CATALOG, ALL);
        for (ConditionalRequirement req : TptRuleSet.CONDITIONAL_REQUIREMENTS) {
            assertThat(rules)
                    .as("rule registered for %s", req.ruleId())
                    .anyMatch(r -> r instanceof ConditionalFieldPresenceRule cf
                            && cf.requirement().ruleId().equals(req.ruleId()));
        }
    }

    @Test
    void targetsCoveredByXfRulesHaveNoGenericPresenceRule() {
        List<Rule> rules = RULE_SET.build(CATALOG, ALL);
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
        List<Rule> rules = RULE_SET.build(CATALOG, ALL);

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
        for (ConditionalRequirement req : TptRuleSet.CONDITIONAL_REQUIREMENTS) {
            assertThat(CATALOG.byNumKey(req.targetFieldNum()))
                    .as("target field %s of %s exists in catalog", req.targetFieldNum(), req.ruleId())
                    .isPresent();
        }
    }

    // ------------------------------------------------------------------ TPT V8 ---

    private static final SpecCatalog CATALOG_V8 =
            new TptTemplate().specLoaderFor(TptTemplate.V8_0).load();
    private static final TptRuleSet RULE_SET_V8 = new TptRuleSet(TptTemplate.V8_0);

    @Test
    void v8DropsTheXfRulesWhoseTargetsAreNotApplicableOrMandatory() {
        assertThat(RULE_SET_V8.isV8OrLater()).isTrue();
        assertThat(new TptRuleSet().isV8OrLater()).isFalse();
        assertThat(TptRuleSet.CONDITIONAL_REQUIREMENTS_V8)
                .extracting(ConditionalRequirement::ruleId)
                .containsExactly("XF-17/INDEX_MARGIN", "XF-18/CALL_PUT_DATE",
                        "XF-19/STRIKE_PRICE", "XF-25/COLLATERAL_VALUE");

        List<Rule> rules = RULE_SET_V8.build(CATALOG_V8, ALL);
        Set<String> ids = rules.stream().map(Rule::id).collect(java.util.stream.Collectors.toSet());
        for (String dropped : TptRuleSet.V8_DROPPED_XF_RULES) {
            assertThat(ids).as("%s must not be registered for V8", dropped).doesNotContain(dropped);
        }
        assertThat(ids).contains("XF-17/INDEX_MARGIN", "XF-25/COLLATERAL_VALUE");
        assertThat(rules).noneMatch(r -> r instanceof CustodianPairRule);
    }

    @Test
    void v8IssuerIdentifiersGetGenericPresenceRules() {
        List<Rule> rules = RULE_SET_V8.build(CATALOG_V8, ALL);
        for (String num : List.of("47", "48", "50", "51", "115", "119")) {
            assertThat(rules)
                    .as("V8: field %s is M and no longer covered by an XF rule", num)
                    .anyMatch(r -> r instanceof PresenceRule pr && pr.id().equals("PRESENCE/" + num + "/SOLVENCY_II"));
        }
        // N/A fields get neither presence nor conditional-presence rules …
        assertThat(rules).noneMatch(r -> (r instanceof PresenceRule || r instanceof ConditionalPresenceRule)
                && r.id().matches("(COND_)?PRESENCE/(116|120|141|35|36|54)/.*"));
        // … but a NotApplicableRule each.
        assertThat(rules.stream().filter(r -> r instanceof NotApplicableRule).map(Rule::id))
                .contains("NOT_APPLICABLE/116", "NOT_APPLICABLE/120", "NOT_APPLICABLE/141",
                        "NOT_APPLICABLE/35", "NOT_APPLICABLE/36", "NOT_APPLICABLE/54")
                .hasSize(18);
    }

    @Test
    void v8LeiRulesForFundIssuerGroupAndCustodianAreTypeless() {
        List<Rule> rules = RULE_SET_V8.build(CATALOG_V8, ALL);
        List<String> leiIds = rules.stream().filter(r -> r instanceof LeiRule).map(Rule::id).toList();
        assertThat(leiIds).containsExactlyInAnyOrder(
                "LEI/47/48", "LEI/50/51", "LEI/81/82", "LEI/84/85", "LEI/115", "LEI/119", "LEI/140");

        List<String> leiIdsV7 = RULE_SET.build(CATALOG, ALL).stream()
                .filter(r -> r instanceof LeiRule).map(Rule::id).toList();
        assertThat(leiIdsV7).contains("LEI/115/116", "LEI/119/120", "LEI/140/141");
    }

    @Test
    void v8FloatingRateRequirementsExcludeTheNotApplicableIndexFields() {
        InterestRateTypeRule v8 = RULE_SET_V8.build(CATALOG_V8, ALL).stream()
                .filter(r -> r instanceof InterestRateTypeRule).map(r -> (InterestRateTypeRule) r)
                .findFirst().orElseThrow();
        assertThat(v8.requiredWhenFloating()).containsExactly("34", "37");

        InterestRateTypeRule v7 = RULE_SET.build(CATALOG, ALL).stream()
                .filter(r -> r instanceof InterestRateTypeRule).map(r -> (InterestRateTypeRule) r)
                .findFirst().orElseThrow();
        assertThat(v7.requiredWhenFloating()).containsExactly("34", "35", "36", "37");
    }

    @Test
    void v7RuleSetHasNoNotApplicableRulesBeyondItsOwnNaFields() {
        // V7 flags only two fields N/A — the rule is generic, so it is registered there too.
        List<Rule> rules = RULE_SET.build(CATALOG, ALL);
        assertThat(rules.stream().filter(r -> r instanceof NotApplicableRule).count()).isEqualTo(2);
    }
}
