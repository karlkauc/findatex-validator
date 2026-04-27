package com.tpt.validator.validation.rules;

import com.tpt.validator.domain.TptRow;
import com.tpt.validator.spec.FieldSpec;
import com.tpt.validator.template.api.ProfileKey;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Rule;
import com.tpt.validator.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;

/** Field is mandatory for a profile and CIC-applicable for the row. */
public final class PresenceRule implements Rule {

    private final FieldSpec spec;
    private final ProfileKey profile;

    public PresenceRule(FieldSpec spec, ProfileKey profile) {
        this.spec = spec;
        this.profile = profile;
    }

    @Override
    public String id() { return "PRESENCE/" + spec.numKey() + "/" + profile.code(); }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        if (!ctx.activeProfiles().contains(profile)) return List.of();
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            if (!CicApplicability.applies(spec, row)) continue;
            if (row.stringValue(spec).isEmpty()) {
                out.add(Finding.error(
                        id(),
                        profile,
                        spec.numKey(),
                        spec.numData(),
                        row.rowIndex(),
                        null,
                        "Mandatory field for " + profile.displayName() + " is missing"));
            }
        }
        return out;
    }
}
