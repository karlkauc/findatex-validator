package com.findatex.validator.template.eet;

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
import com.findatex.validator.validation.rules.PresenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalFieldPresenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalRequirement;
import com.findatex.validator.validation.rules.crossfield.EetVersionRule;
import com.findatex.validator.validation.rules.crossfield.FieldPredicate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule set for the European ESG Template (EET). Cross-field requirements are sourced strictly
 * from the per-field {@code DEFINITION} / {@code COMMENT} text in the bundled spec XLSX
 * (see {@link com.findatex.validator.spec.ManifestDrivenSpecLoader}). Items marked
 * {@code TODO(eet-xf): needs SME validation} require regulatory expertise to confirm and are
 * intentionally omitted until a SFDR / MiFID / IDD subject-matter expert reviews them.
 */
public final class EetRuleSet implements TemplateRuleSet {

    /**
     * Cross-field "if X then Y must be present" rules. Fields are referenced by their {@code NUM}
     * column value (1-based, matches the {@code FieldSpec.numKey()} produced by the manifest
     * loader). Source: V1.1.3 spec rows 8, 27–48 and 63 (SFDR product-type-driven mandatoriness).
     */
    public static final List<ConditionalRequirement> CONDITIONAL_REQUIREMENTS = List.of(
            // Spec field 28 (NUM=28, 20050_..._SFDR_Product_Type_Eligible) is "Conditional to
            // 20040 set to 0": when SFDR Product Type (field 27) = 0 (out-of-scope), the
            // eligibility classification (field 28) must be supplied.
            new ConditionalRequirement("EET-XF-SFDR-OUT-OF-SCOPE",
                    "27", FieldPredicate.EqualsAny.of("0"),
                    "28", Severity.ERROR),

            // SFDR Article 8 product → minimum proportion of Art-8-aligned look-through
            // investments (field 30) is required.
            new ConditionalRequirement("EET-XF-ART8-MIN-LT",
                    "27", FieldPredicate.EqualsAny.of("8"),
                    "30", Severity.ERROR),

            // SFDR Article 9 product → minimum proportion of Art-9-aligned look-through
            // investments (field 31) is required.
            new ConditionalRequirement("EET-XF-ART9-MIN-LT",
                    "27", FieldPredicate.EqualsAny.of("9"),
                    "31", Severity.ERROR),

            // SFDR Article 8 product with sustainable investments (field 40 = Y) →
            // minimum sustainable-investment proportion (field 41) is required.
            new ConditionalRequirement("EET-XF-ART8-MIN-SI",
                    "40", FieldPredicate.EqualsAny.of("Y", "Yes", "TRUE", "1"),
                    "41", Severity.ERROR),

            // SFDR Article 9 product → minimum sustainable-investment proportion with
            // environmental objective (field 45) is required.
            new ConditionalRequirement("EET-XF-ART9-MIN-ENV",
                    "27", FieldPredicate.EqualsAny.of("9"),
                    "45", Severity.ERROR),

            // SFDR Article 9 product → minimum sustainable-investment proportion with
            // social objective (field 48) is required.
            new ConditionalRequirement("EET-XF-ART9-MIN-SOC",
                    "27", FieldPredicate.EqualsAny.of("9"),
                    "48", Severity.ERROR)
    );

    /**
     * Targets handled by an XF rule above must be removed from the generic Presence /
     * ConditionalPresence registration so the same missing value isn't reported twice.
     */
    private static final Set<String> ADDITIONALLY_SUPPRESSED_FROM_GENERIC_RULES = Set.of();

    private final String expectedVersionToken;

    public EetRuleSet() {
        this("V1.1.3");
    }

    public EetRuleSet(TemplateVersion version) {
        this(version.version());
    }

    public EetRuleSet(String expectedVersionToken) {
        this.expectedVersionToken = expectedVersionToken;
    }

    @Override
    public List<Rule> build(SpecCatalog catalog, Set<ProfileKey> selectedProfiles) {
        List<Rule> rules = new ArrayList<>();

        Set<String> handledByXf = CONDITIONAL_REQUIREMENTS.stream()
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
        }

        rules.add(new EetVersionRule(expectedVersionToken));

        for (ConditionalRequirement req : CONDITIONAL_REQUIREMENTS) {
            rules.add(new ConditionalFieldPresenceRule(req));
        }

        // TODO(eet-xf): needs SME validation
        //  - Taxonomy alignment sum-checks (fields 42-44, 46-47) — relationship between
        //    Art-8/Art-9 sustainable-investment minimums and taxonomy/social/environmental
        //    sub-categories needs RTS confirmation before encoding as hard rules.
        //  - PAI consideration (entity-level field 8 → PAI indicator block) requires the
        //    full PAI-indicator field map and the "considers PAI = Y" gating semantics.
        //  - Negative SFDR constraint: field 27 = 0 ("not in scope") should arguably forbid
        //    Art-8/Art-9 fields from being populated. Phrasing as a hard error needs SME OK.

        return rules;
    }
}
