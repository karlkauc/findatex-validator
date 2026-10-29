package com.findatex.validator.template.tpt;

import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.Flag;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateRuleSet;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.rules.ConditionalPresenceRule;
import com.findatex.validator.validation.rules.FormatRule;
import com.findatex.validator.validation.rules.IsinRule;
import com.findatex.validator.validation.rules.LeiRule;
import com.findatex.validator.validation.rules.NotApplicableRule;
import com.findatex.validator.validation.rules.PresenceRule;
import com.findatex.validator.validation.rules.crossfield.CashPercentageRule;
import com.findatex.validator.validation.rules.crossfield.CompleteScrDeliveryRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalFieldPresenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalRequirement;
import com.findatex.validator.validation.rules.crossfield.CouponFrequencyRule;
import com.findatex.validator.validation.rules.crossfield.CustodianPairRule;
import com.findatex.validator.validation.rules.crossfield.DateOrderRule;
import com.findatex.validator.validation.rules.crossfield.FieldPredicate;
import com.findatex.validator.validation.rules.crossfield.InterestRateTypeRule;
import com.findatex.validator.validation.rules.crossfield.MaturityAfterReportingRule;
import com.findatex.validator.validation.rules.crossfield.NavConsistencyRule;
import com.findatex.validator.validation.rules.crossfield.PikRule;
import com.findatex.validator.validation.rules.crossfield.PositionWeightSumRule;
import com.findatex.validator.validation.rules.crossfield.TptVersionRule;
import com.findatex.validator.validation.rules.crossfield.UnderlyingCicRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TPT rule set. Holds the canonical {@link ConditionalRequirement} list and the rule-build logic
 * that was historically in {@code com.findatex.validator.validation.RuleRegistry} (since removed).
 *
 * <p>The rule set is version-aware. TPT V8 (final, 2026-10-08) flags 18 fields "N/A — must be
 * left empty" (among them 29, 31, 35, 36, 44, 116, 120, 141) and promotes the issuer/group
 * identifiers 47, 50, 81, 84/85 to mandatory, so the V7 cross-field conditionals that target
 * those fields are dropped for V8 and the generic presence rules take over. V8 also requires an
 * LEI outright in 115, 119 and 140 (their former type columns are N/A).
 */
public final class TptRuleSet implements TemplateRuleSet {

    private final String expectedVersionToken;
    private final boolean v8OrLater;

    /** Backwards-compatible default: rules built against TPT V7.0 expectations. */
    public TptRuleSet() {
        this("V7.0");
    }

    public TptRuleSet(TemplateVersion version) {
        this(version.version());
    }

    public TptRuleSet(String expectedVersionToken) {
        this.expectedVersionToken = expectedVersionToken;
        this.v8OrLater = majorVersion(expectedVersionToken) >= 8;
    }

    /** {@code "V8.0"} → 8, {@code "7.0"} → 7; unparseable tokens count as V7 (legacy default). */
    static int majorVersion(String token) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("V?(\\d+)").matcher(token == null ? "" : token.trim());
        return m.find() ? Integer.parseInt(m.group(1)) : 7;
    }

    public boolean isV8OrLater() { return v8OrLater; }

    /**
     * Cross-field "if X then Y must be present" rules sourced from the per-CIC qualifier text in
     * the spec (e.g. {@code "x\nif item 48 set to \"1\""}). These supersede {@link PresenceRule} /
     * {@link ConditionalPresenceRule} for their target fields so we only emit a finding when the
     * trigger condition actually applies on the row in question.
     */
    public static final List<ConditionalRequirement> CONDITIONAL_REQUIREMENTS = List.of(
            // V7 only — target field is N/A in V8 or the trigger qualifier was removed from the spec.
            new ConditionalRequirement("XF-16/THIRD_CURRENCY_WEIGHT",
                    "29", FieldPredicate.NotBlank.INSTANCE,
                    "31", Severity.ERROR),

            new ConditionalRequirement("XF-17/INDEX_TYPE",
                    "34", FieldPredicate.NotBlank.INSTANCE,
                    "35", Severity.ERROR),
            new ConditionalRequirement("XF-17/INDEX_NAME",
                    "34", FieldPredicate.NotBlank.INSTANCE,
                    "36", Severity.ERROR),
            new ConditionalRequirement("XF-17/INDEX_MARGIN",
                    "34", FieldPredicate.NotBlank.INSTANCE,
                    "37", Severity.ERROR),

            new ConditionalRequirement("XF-18/CALL_PUT_DATE",
                    "42", FieldPredicate.EqualsAny.of("Cal", "Put"),
                    "43", Severity.ERROR),
            new ConditionalRequirement("XF-18/OPTION_DIRECTION",
                    "42", FieldPredicate.EqualsAny.of("Cal", "Put"),
                    "44", Severity.ERROR),
            new ConditionalRequirement("XF-19/STRIKE_PRICE",
                    "42", FieldPredicate.NotBlank.INSTANCE,
                    "45", Severity.ERROR),

            new ConditionalRequirement("XF-20/ISSUER_LEI_PRESENT",
                    "48", FieldPredicate.EqualsAny.of("1"),
                    "47", Severity.ERROR),
            new ConditionalRequirement("XF-21/GROUP_LEI_PRESENT",
                    "51", FieldPredicate.EqualsAny.of("1"),
                    "50", Severity.ERROR),
            new ConditionalRequirement("XF-22/UNDERLYING_GROUP_LEI",
                    "85", FieldPredicate.EqualsAny.of("1"),
                    "84", Severity.ERROR),
            new ConditionalRequirement("XF-23/FUND_ISSUER_LEI",
                    "116", FieldPredicate.EqualsAny.of("1"),
                    "115", Severity.ERROR),
            new ConditionalRequirement("XF-24/FUND_GROUP_LEI",
                    "120", FieldPredicate.EqualsAny.of("1"),
                    "119", Severity.ERROR),

            new ConditionalRequirement("XF-25/COLLATERAL_VALUE",
                    "138", FieldPredicate.EqualsAny.of("1", "2", "3"),
                    "139", Severity.ERROR)
    );

    /**
     * Rule ids from {@link #CONDITIONAL_REQUIREMENTS} that do not apply to TPT V8: their target
     * is "N/A — must be left empty" (31, 35, 36, 44, 115→116, 119→120 trigger columns) or the
     * target became unconditionally mandatory / the per-CIC qualifier was dropped (47, 50, 84),
     * in which case the generic {@code PresenceRule} / {@code ConditionalPresenceRule} applies.
     */
    public static final Set<String> V8_DROPPED_XF_RULES = Set.of(
            "XF-16/THIRD_CURRENCY_WEIGHT",
            "XF-17/INDEX_TYPE",
            "XF-17/INDEX_NAME",
            "XF-18/OPTION_DIRECTION",
            "XF-20/ISSUER_LEI_PRESENT",
            "XF-21/GROUP_LEI_PRESENT",
            "XF-22/UNDERLYING_GROUP_LEI",
            "XF-23/FUND_ISSUER_LEI",
            "XF-24/FUND_GROUP_LEI");

    /** The V7 list without {@link #V8_DROPPED_XF_RULES}. */
    public static final List<ConditionalRequirement> CONDITIONAL_REQUIREMENTS_V8 =
            CONDITIONAL_REQUIREMENTS.stream()
                    .filter(r -> !V8_DROPPED_XF_RULES.contains(r.ruleId()))
                    .toList();

    /** The declarative XF-16..XF-25 subset active for this rule set's version. */
    public List<ConditionalRequirement> conditionalRequirements() {
        return v8OrLater ? CONDITIONAL_REQUIREMENTS_V8 : CONDITIONAL_REQUIREMENTS;
    }

    /**
     * Target fields whose generic Presence / ConditionalPresence registration must be suppressed
     * because they are handled by a hand-coded XF rule, or because the spec qualifier is a
     * narrative condition that cannot be evaluated algorithmically. See {@code docs/ROADMAP.md} 1.2
     * for the field-95 carve-out.
     */
    private static final Set<String> ADDITIONALLY_SUPPRESSED_FROM_GENERIC_RULES = Set.of(
            "33", "34", "67", "95"
    );

    @Override
    public List<Rule> build(SpecCatalog catalog, Set<ProfileKey> selectedProfiles) {
        List<Rule> rules = new ArrayList<>();

        // Targets covered by an XF conditional rule are removed from the generic
        // presence/conditional-presence registration so we don't emit two findings
        // per missing value.
        List<ConditionalRequirement> conditionals = conditionalRequirements();
        Set<String> handledByXf = conditionals.stream()
                .map(ConditionalRequirement::targetFieldNum)
                .collect(Collectors.toCollection(HashSet::new));
        handledByXf.addAll(ADDITIONALLY_SUPPRESSED_FROM_GENERIC_RULES);

        for (FieldSpec spec : catalog.fields()) {
            rules.add(new FormatRule(spec));

            boolean suppress = handledByXf.contains(spec.numKey());
            for (ProfileKey p : selectedProfiles) {
                Flag f = spec.flag(p);
                if (f == Flag.M && !suppress) rules.add(new PresenceRule(spec, p));
                if (f == Flag.C && !suppress) rules.add(new ConditionalPresenceRule(spec, p));
            }
            // "N/A — must be left empty" is a property of the template, not of a profile: the
            // spec's primary (Solvency II) column carries it. One rule per field, reported once.
            if (spec.flag(TptProfiles.SOLVENCY_II) == Flag.NA
                    && selectedProfiles.contains(TptProfiles.SOLVENCY_II)) {
                rules.add(new NotApplicableRule(spec, TptProfiles.SOLVENCY_II));
            }
        }

        // Identifier checksum rules. Fields 15 and 69 use the full
        // ISIN/CUSIP/SEDOL/... closed list where "1" = ISIN; fields 48, 51, 82,
        // 85 (and in V7 also 116, 120, 141) use the short list where "1" = LEI.
        rules.add(new IsinRule("14", "15"));        // instrument
        rules.add(new IsinRule("68", "69"));        // underlying
        rules.add(new LeiRule("47", "48"));         // issuer
        rules.add(new LeiRule("50", "51"));         // issuer group
        rules.add(new LeiRule("81", "82"));         // underlying issuer
        rules.add(new LeiRule("84", "85"));         // underlying issuer group
        if (v8OrLater) {
            // V8: 115/119/140 "must contain LEIs"; their type columns 116/120/141 are N/A.
            rules.add(new LeiRule("115"));          // fund issuer
            rules.add(new LeiRule("119"));          // fund issuer group
            rules.add(new LeiRule("140"));          // custodian
        } else {
            rules.add(new LeiRule("115", "116"));   // fund issuer
            rules.add(new LeiRule("119", "120"));   // fund issuer group
            rules.add(new LeiRule("140", "141"));   // custodian
        }

        // Hand-written cross-field rules for non-uniform conditions.
        rules.add(new CompleteScrDeliveryRule());
        rules.add(new PositionWeightSumRule());
        rules.add(new CashPercentageRule());
        rules.add(new NavConsistencyRule());
        rules.add(new CouponFrequencyRule());
        if (!v8OrLater) rules.add(new CustodianPairRule());   // 141 is N/A in V8
        rules.add(new InterestRateTypeRule(v8OrLater
                ? InterestRateTypeRule.FLOATING_FIELDS_V8
                : InterestRateTypeRule.FLOATING_FIELDS_V7));
        rules.add(new DateOrderRule());
        catalog.byNumKey("39").ifPresent(spec -> rules.add(new MaturityAfterReportingRule(spec)));
        rules.add(new PikRule());
        catalog.byNumKey("67").ifPresent(spec -> rules.add(new UnderlyingCicRule(spec)));
        rules.add(new TptVersionRule(expectedVersionToken));

        // Declarative cross-field conditionals (XF-16..XF-25, V8 subset where applicable).
        for (ConditionalRequirement req : conditionals) {
            rules.add(new ConditionalFieldPresenceRule(req));
        }

        return rules;
    }
}
