package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;
import com.findatex.validator.validation.rules.RuleDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** XF-08: field 38 (Coupon frequency) ∈ {0,1,2,4,12,52}. */
public final class CouponFrequencyRule implements Rule {

    private static final Set<String> ALLOWED = Set.of("0", "1", "2", "4", "12", "52");

    @Override public String id() { return "XF-08/COUPON_FREQUENCY"; }

    public RuleDoc describe() {
        return new RuleDoc(
                "Field 38 (Coupon payment frequency) must be one of {0, 1, 2, 4, 12, 52}.",
                Severity.ERROR,
                List.of("38"),
                List.of("38"));
    }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            String v = row.stringValue("38").orElse(null);
            if (v == null) continue;
            String norm = v.trim();
            // tolerate trailing ".0"
            if (norm.endsWith(".0")) norm = norm.substring(0, norm.length() - 2);
            if (!ALLOWED.contains(norm)) {
                out.add(Finding.error(
                        id(), null, "38", "Field 38 (Coupon payment frequency)",
                        row.rowIndex(), v,
                        "Coupon frequency must be one of {0, 1, 2, 4, 12, 52}"));
            }
        }
        return out;
    }
}
