package com.tpt.validator.validation.rules.crossfield;

import com.tpt.validator.domain.CicCode;
import com.tpt.validator.domain.TptRow;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Rule;
import com.tpt.validator.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** XF-14: field 67 (underlying CIC) mandatory iff main CIC ∈ {2, A, B, C, D, F}. */
public final class UnderlyingCicRule implements Rule {

    private static final Set<String> NEEDS_UNDERLYING = Set.of("2", "A", "B", "C", "D", "F");

    @Override public String id() { return "XF-14/UNDERLYING_CIC"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            CicCode cic = row.cic().orElse(null);
            if (cic == null) continue;
            if (!NEEDS_UNDERLYING.contains(cic.categoryDigit())) continue;
            if (row.stringValue("67").isEmpty()) {
                out.add(Finding.error(
                        id(), null, "67", "Field 67 (CIC of the underlying asset)",
                        row.rowIndex(), null,
                        "Underlying CIC is mandatory for instruments of CIC " + cic.categoryDigit()));
            }
        }
        return out;
    }
}
