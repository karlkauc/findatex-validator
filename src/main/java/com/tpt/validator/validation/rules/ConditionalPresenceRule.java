package com.tpt.validator.validation.rules;

import com.tpt.validator.domain.TptRow;
import com.tpt.validator.spec.FieldSpec;
import com.tpt.validator.spec.Profile;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Rule;
import com.tpt.validator.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Field is conditional for a profile — required when the row's CIC matches
 * the field's applicability list. Other conditional triggers (XF rules) are
 * handled separately as cross-field rules.
 */
public final class ConditionalPresenceRule implements Rule {

    private final FieldSpec spec;
    private final Profile profile;

    public ConditionalPresenceRule(FieldSpec spec, Profile profile) {
        this.spec = spec;
        this.profile = profile;
    }

    @Override
    public String id() { return "COND_PRESENCE/" + spec.numKey() + "/" + profile.name(); }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        if (!ctx.activeProfiles().contains(profile)) return List.of();
        if (spec.appliesToAllCic()) return List.of();
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            if (!CicApplicability.applies(spec, row)) continue;
            if (row.cic().isEmpty()) continue;          // can only enforce when CIC is parseable
            if (row.stringValue(spec).isEmpty()) {
                out.add(Finding.warn(
                        id(),
                        profile,
                        spec.numKey(),
                        spec.numData(),
                        row.rowIndex(),
                        null,
                        "Conditional field for " + profile.displayName()
                                + " is missing for CIC " + row.cic().get().raw()));
            }
        }
        return out;
    }
}
