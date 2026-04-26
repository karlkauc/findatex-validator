package com.tpt.validator.validation.rules.crossfield;

import com.tpt.validator.domain.TptRow;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Rule;
import com.tpt.validator.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * XF-10: Field 32 = Floating ⇒ fields 34..37 mandatory; Fixed ⇒ field 33 mandatory.
 * Variant-tolerant: matches "Floating", "Float", "Variable", "Fixed".
 */
public final class InterestRateTypeRule implements Rule {

    @Override public String id() { return "XF-10/INTEREST_RATE_TYPE"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            String t = row.stringValue("32").orElse("").toLowerCase(Locale.ROOT).trim();
            if (t.isEmpty()) continue;

            if (t.startsWith("float") || t.startsWith("variable")) {
                for (String fk : new String[]{"34", "35", "36", "37"}) {
                    if (row.stringValue(fk).isEmpty()) {
                        out.add(Finding.error(
                                id(), null, fk, "Field " + fk,
                                row.rowIndex(), null,
                                "Field " + fk + " is mandatory when field 32 = Floating/Variable"));
                    }
                }
            } else if (t.startsWith("fix")) {
                if (row.stringValue("33").isEmpty()) {
                    out.add(Finding.error(
                            id(), null, "33", "Field 33 (Coupon rate)",
                            row.rowIndex(), null,
                            "Field 33 is mandatory when field 32 = Fixed"));
                }
            }
        }
        return out;
    }
}
