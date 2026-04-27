package com.findatex.validator.template.emt;

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
import com.findatex.validator.validation.rules.crossfield.EmtVersionRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Rule set for the European MiFID Template (EMT). Generic {@link FormatRule},
 * {@link PresenceRule} and {@link ConditionalPresenceRule} cover the per-field flags from the
 * manifest. Cross-field MiFID II target-market and cost-arithmetic rules are deferred until
 * the spec's per-field {@code COMMENT} text can be reviewed by an MiFID SME — see TODO at the
 * end of {@link #build(SpecCatalog, Set)}.
 */
public final class EmtRuleSet implements TemplateRuleSet {

    private final String expectedVersionToken;

    public EmtRuleSet() {
        this("V4.3");
    }

    public EmtRuleSet(TemplateVersion version) {
        this(version.version());
    }

    public EmtRuleSet(String expectedVersionToken) {
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

        rules.add(new EmtVersionRule(expectedVersionToken));

        // TODO(emt-xf): needs SME validation
        //  - Target Market block: when "Eligible Investor Type" indicates a restricted set,
        //    the corresponding negative-target-market and risk-tolerance fields become required.
        //  - Cost arithmetic: total ongoing costs should equal the sum of subcomponents
        //    (entry / management / transaction / incidental) — confirm tolerance per RTS.
        //  - SRI consistency: the synthetic risk indicator (1–7) should agree with declared
        //    volatility class on PRIIPs-relevant products.

        return rules;
    }
}
