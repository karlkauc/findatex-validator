package com.findatex.validator.template.ept;

import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.Flag;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateRuleSet;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.rules.ConditionalPresenceRule;
import com.findatex.validator.validation.rules.FormatRule;
import com.findatex.validator.validation.rules.PresenceRule;
import com.findatex.validator.validation.rules.crossfield.EptVersionRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Rule set for the European PRIIPs Template (EPT). Generic Format/Presence/ConditionalPresence
 * rules cover the per-field flags from the manifest; {@link EptVersionRule} enforces the
 * declared spec version. PRIIPs-specific cross-field logic (performance scenarios, SRI
 * consistency, cost-component sums) is deferred until reviewed by a PRIIPs SME — see TODO.
 */
public final class EptRuleSet implements TemplateRuleSet {

    private final String expectedVersionToken;

    public EptRuleSet() {
        this("V21");
    }

    public EptRuleSet(TemplateVersion version) {
        // EPT version field carries compact tokens like "V20", "V21", "V21UK"; the manifest
        // version field is "V2.0" / "V2.1" — strip the dot for the field-value comparison.
        this(version.version().replace(".", ""));
    }

    public EptRuleSet(String expectedVersionToken) {
        this.expectedVersionToken = expectedVersionToken;
    }

    @Override
    public List<Rule> build(SpecCatalog catalog, Set<ProfileKey> selectedProfiles) {
        List<Rule> rules = new ArrayList<>();

        for (FieldSpec spec : catalog.fields()) {
            rules.add(new FormatRule(spec));
            for (ProfileKey p : selectedProfiles) {
                Flag f = spec.flag(p);
                if (f == Flag.M) rules.add(new PresenceRule(spec, p));
                if (f == Flag.C) rules.add(new ConditionalPresenceRule(spec, p));
            }
        }

        rules.add(new EptVersionRule(expectedVersionToken));

        // TODO(ept-xf): needs SME validation
        //  - Performance scenarios: stress / unfavourable / moderate / favourable must all
        //    be populated for PRIIPs Categories 1-4. Encoding requires identifying the four
        //    field NUMs (RTS Annex IV) and the category-discriminator field.
        //  - SRI consistency: synthetic risk indicator (1-7) must agree with the credit risk
        //    measure and market risk measure when both present.
        //  - Cost components: total ongoing costs = sum of one-off + recurring + incidental
        //    over the recommended holding period.

        return rules;
    }
}
