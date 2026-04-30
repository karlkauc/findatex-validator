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
import com.findatex.validator.validation.rules.crossfield.ConditionalAnyFieldPresenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalAnyOfRequirement;
import com.findatex.validator.validation.rules.crossfield.ConditionalFieldAbsenceRule;
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
     * Art-8 / Art-9 fields that must be EMPTY when the SFDR product type (NUM=27) is "0"
     * ("not in scope"). Identical across V1.1.2 and V1.1.3. Wired via
     * {@link ConditionalFieldAbsenceRule} with severity = WARNING (promotion to ERROR awaits
     * SFDR SME sign-off — see footer comment).
     */
    private static final List<String> ART_FIELDS_FORBIDDEN_WHEN_OUT_OF_SCOPE = List.of(
            "30", "31", "40", "41", "42", "43", "44", "45", "46", "47", "48");

    /**
     * PAI indicator block — every NUM in this list becomes mandatory when the per-product
     * PAI gating field NUM=33 ({@code 20100_..._Does_This_Product_Consider_Principle_Adverse_Impact})
     * is set to "Y". Identical across V1.1.2 and V1.1.3 (verified by
     * {@code tools/audit_eet_pai_block.py}: 27 NUMs each, identical contents).
     *
     * <p>NUMs 103/104 are PAI-snapshot metadata (frequency, reference date); 106..202 are
     * the per-indicator "Considered_In_The_Investment_Strategy" Y/N flags. Only NUM=106
     * carries the explicit "Conditional to item 20100 set to Yes" comment in the spec; the
     * remainder of the block is gated implicitly — wiring them all matches operator intent.
     */
    private static final List<String> PAI_BLOCK = List.of(
            "103", "104",
            "106", "110", "114", "118", "122", "126", "130", "134", "138",
            "142", "146", "150", "154", "158", "162", "166", "170", "174",
            "178", "182", "186", "190", "194", "198", "202");

    /**
     * Soft "at-least-one-of" attribution rule for the Art-8 minimum sustainable
     * investment proportion (NUM=41): when reported, at least one of the sub-attribution
     * Y/N flags (42 EU-Taxonomy / 43 non-EU-environmental / 44 social) must be "Y" so
     * the minimum is attributable to a category. Severity = WARNING — the spec text
     * does NOT mandate a sum-check (these are independent Y/N answers; encoding a
     * stricter rule without RTS evidence would invent regulatory logic).
     */
    private static final ConditionalAnyOfRequirement ART8_MIN_SI_SPLIT = new ConditionalAnyOfRequirement(
            "EET-XF-ART8-MIN-SI-SPLIT", "41", FieldPredicate.NotBlank.INSTANCE,
            List.of("42", "43", "44"), Severity.WARNING);

    /** Art-9 counterpart of {@link #ART8_MIN_SI_SPLIT}: NUM=45 minimum environmental → 46/47. */
    private static final ConditionalAnyOfRequirement ART9_MIN_ENV_SPLIT = new ConditionalAnyOfRequirement(
            "EET-XF-ART9-MIN-ENV-SPLIT", "45", FieldPredicate.NotBlank.INSTANCE,
            List.of("46", "47"), Severity.WARNING);

    /**
     * Targets handled by an XF rule above must be removed from the generic Presence /
     * ConditionalPresence registration so the same missing value isn't reported twice.
     * The PAI block is added here because every PAI indicator carries M/C flags on
     * SFDR_ENTITY profiles and would otherwise double-report missing values when
     * NUM=33="Y".
     */
    private static final Set<String> ADDITIONALLY_SUPPRESSED_FROM_GENERIC_RULES = Set.copyOf(PAI_BLOCK);

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

        // Negative SFDR constraint — when NUM=27 = "0" (out of SFDR scope),
        // every Art-8/Art-9-only field must be empty. WARNING severity until SME signs off.
        for (String num : ART_FIELDS_FORBIDDEN_WHEN_OUT_OF_SCOPE) {
            rules.add(new ConditionalFieldAbsenceRule(new ConditionalRequirement(
                    "EET-XF-ART" + num + "-MUST-BE-ABSENT",
                    "27", FieldPredicate.EqualsAny.of("0"),
                    num, Severity.WARNING)));
        }

        // PAI gating — when NUM=33 = "Y" (product considers Principal Adverse Impacts),
        // every NUM in the PAI block must be present.
        for (String num : PAI_BLOCK) {
            rules.add(new ConditionalFieldPresenceRule(new ConditionalRequirement(
                    "EET-XF-PAI-" + num,
                    "33", FieldPredicate.EqualsAny.of("Y", "Yes", "TRUE", "1"),
                    num, Severity.ERROR)));
        }

        // Taxonomy at-least-one-of attribution: NUM=41 → {42,43,44}; NUM=45 → {46,47}.
        rules.add(new ConditionalAnyFieldPresenceRule(ART8_MIN_SI_SPLIT));
        rules.add(new ConditionalAnyFieldPresenceRule(ART9_MIN_ENV_SPLIT));

        // Wired (was DEFERRED, now done — see docs/EET_AUDIT_V113.md):
        //  - Negative SFDR constraint (EET-XF-ART*-MUST-BE-ABSENT, severity=WARNING).
        //  - PAI gating on NUM=33 (= 20100_..._Does_This_Product_Consider_Principle_Adverse_Impact),
        //    NOT NUM=8 — the historical TODO was incorrect: NUM=8 is the entity-level
        //    SFDR-reporting Y/N flag, a separate concept.
        //  - Taxonomy at-least-one-of split (soft rule — the spec text does NOT
        //    impose a sum-check, only that the minimum from 41/45 must be attributable
        //    to one of the sub-categories).
        //
        // Still DEFERRED (require regulatory SME):
        //  - Quantitative taxonomy sum-check across 42/43/44 vs 41 — RTS does not
        //    currently mandate that the components sum to the parent; encoding as a
        //    hard rule would invent regulatory logic.
        //  - PAI value fields (NUMs 105/109/113/.../237) gated by "Coverage > 0%" —
        //    requires identifying the Coverage source field and adding a numeric
        //    FieldPredicate variant.
        //  - Promotion of EET-XF-ART*-MUST-BE-ABSENT severity from WARNING to ERROR
        //    after SFDR SME sign-off.

        return rules;
    }
}
