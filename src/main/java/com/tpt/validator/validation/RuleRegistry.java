package com.tpt.validator.validation;

import com.tpt.validator.spec.FieldSpec;
import com.tpt.validator.spec.Flag;
import com.tpt.validator.spec.Profile;
import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.validation.rules.ConditionalPresenceRule;
import com.tpt.validator.validation.rules.FormatRule;
import com.tpt.validator.validation.rules.IsinRule;
import com.tpt.validator.validation.rules.LeiRule;
import com.tpt.validator.validation.rules.PresenceRule;
import com.tpt.validator.validation.rules.crossfield.CashPercentageRule;
import com.tpt.validator.validation.rules.crossfield.CompleteScrDeliveryRule;
import com.tpt.validator.validation.rules.crossfield.CouponFrequencyRule;
import com.tpt.validator.validation.rules.crossfield.CustodianPairRule;
import com.tpt.validator.validation.rules.crossfield.DateOrderRule;
import com.tpt.validator.validation.rules.crossfield.InterestRateTypeRule;
import com.tpt.validator.validation.rules.crossfield.MaturityAfterReportingRule;
import com.tpt.validator.validation.rules.crossfield.NavConsistencyRule;
import com.tpt.validator.validation.rules.crossfield.PikRule;
import com.tpt.validator.validation.rules.crossfield.PositionWeightSumRule;
import com.tpt.validator.validation.rules.crossfield.TptVersionRule;
import com.tpt.validator.validation.rules.crossfield.UnderlyingCicRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RuleRegistry {

    private RuleRegistry() {}

    public static List<Rule> build(SpecCatalog catalog, Set<Profile> profiles) {
        List<Rule> rules = new ArrayList<>();

        for (FieldSpec spec : catalog.fields()) {
            // Format rule for every field whose codification is parseable.
            rules.add(new FormatRule(spec));

            // Presence + conditional rules per active profile.
            for (Profile p : profiles) {
                Flag f = spec.flag(p);
                if (f == Flag.M) rules.add(new PresenceRule(spec, p));
                if (f == Flag.C) rules.add(new ConditionalPresenceRule(spec, p));
            }
        }

        // Identifier checksum rules — only meaningful when the codification system field equals 1 (ISIN/LEI).
        rules.add(new IsinRule("14", "15"));        // instrument
        rules.add(new IsinRule("47", "48"));        // issuer (when codification=1 → ISIN)
        rules.add(new IsinRule("50", "51"));        // issuer group
        rules.add(new IsinRule("68", "69"));        // underlying
        rules.add(new IsinRule("81", "82"));        // underlying issuer
        rules.add(new LeiRule("47", "48"));         // issuer LEI also possible (system encoded as 1 in EIOPA combined)
        rules.add(new LeiRule("140", "141"));       // custodian

        // Cross-field rules.
        rules.add(new CompleteScrDeliveryRule());
        rules.add(new PositionWeightSumRule());
        rules.add(new CashPercentageRule());
        rules.add(new NavConsistencyRule());
        rules.add(new CouponFrequencyRule());
        rules.add(new CustodianPairRule());
        rules.add(new InterestRateTypeRule());
        rules.add(new DateOrderRule());
        rules.add(new MaturityAfterReportingRule());
        rules.add(new PikRule());
        rules.add(new UnderlyingCicRule());
        rules.add(new TptVersionRule());

        return rules;
    }
}
