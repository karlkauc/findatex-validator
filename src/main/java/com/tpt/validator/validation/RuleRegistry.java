package com.tpt.validator.validation;

import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.template.api.ProfileKey;
import com.tpt.validator.template.tpt.TptRuleSet;
import com.tpt.validator.validation.rules.crossfield.ConditionalRequirement;

import java.util.List;
import java.util.Set;

/**
 * Thin facade that preserves the legacy entry point. Real rule-build logic lives in
 * {@link TptRuleSet}. Will be removed once {@link ValidationEngine} obtains its rules directly
 * from a {@code TemplateRuleSet} during the controller-level template handoff in Phase 1.
 */
public final class RuleRegistry {

    private static final TptRuleSet TPT_RULE_SET = new TptRuleSet();

    private RuleRegistry() {
    }

    /** @see TptRuleSet#CONDITIONAL_REQUIREMENTS */
    public static final List<ConditionalRequirement> CONDITIONAL_REQUIREMENTS = TptRuleSet.CONDITIONAL_REQUIREMENTS;

    public static List<Rule> build(SpecCatalog catalog, Set<ProfileKey> profiles) {
        return TPT_RULE_SET.build(catalog, profiles);
    }
}
