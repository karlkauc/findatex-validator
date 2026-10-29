package com.findatex.validator.validation.rules;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.Flag;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A field the spec flags {@link Flag#NA} for the primary profile is "retained for technical
 * reasons only" and must be left empty (TPT V8 Technical Annex: 18 such fields, e.g. 54, 116,
 * 120, 141). Any non-blank value is reported as a WARNING — producers migrating from V7 often
 * still fill them. The finding does not enter the quality score (its prefix is not bucketed by
 * {@code QualityScorer}).
 */
public final class NotApplicableRule implements Rule {

    public static final String ID_PREFIX = "NOT_APPLICABLE/";

    private final FieldSpec spec;
    private final ProfileKey profile;

    public NotApplicableRule(FieldSpec spec, ProfileKey profile) {
        this.spec = spec;
        this.profile = profile;
    }

    public FieldSpec spec() { return spec; }

    public ProfileKey profile() { return profile; }

    @Override
    public String id() { return ID_PREFIX + spec.numKey(); }

    public RuleDoc describe() {
        return new RuleDoc(
                "Field " + spec.numKey() + " is flagged N/A (no longer maintained, kept for"
                        + " technical reasons) and must be left empty.",
                Severity.WARNING,
                List.of(spec.numKey()),
                List.of(spec.numKey()));
    }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        if (!ctx.activeProfiles().contains(profile)) return List.of();
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            String value = row.stringValue(spec).orElse(null);
            if (value == null) continue;
            out.add(Finding.warn(
                    id(),
                    profile,
                    spec.numKey(),
                    spec.name(),
                    row.rowIndex(),
                    value,
                    "Field is no longer maintained (N/A) and must be left empty"));
        }
        return out;
    }
}
