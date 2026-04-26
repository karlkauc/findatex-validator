package com.tpt.validator.validation.rules.crossfield;

import com.tpt.validator.domain.TptRow;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Rule;
import com.tpt.validator.validation.ValidationContext;

import java.util.List;

/** XF-04: Σ field 26 (PositionWeight) ≈ 1 within tolerance. */
public final class PositionWeightSumRule implements Rule {

    private static final double TOLERANCE = 0.02; // ±2 % to allow rounding/derivative weights

    @Override public String id() { return "XF-04/POSITION_WEIGHT_SUM"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        double sum = 0;
        int counted = 0;
        for (TptRow row : ctx.file().rows()) {
            String v = row.stringValue("26").orElse(null);
            if (v == null) continue;
            try {
                sum += Double.parseDouble(v.replace(",", "."));
                counted++;
            } catch (NumberFormatException ignore) { /* format rule will flag */ }
        }
        if (counted == 0) return List.of();
        if (Math.abs(sum - 1.0) > TOLERANCE) {
            return List.of(Finding.warn(
                    id(), null, "26", "Σ Position weight (field 26)",
                    null, String.format("%.4f", sum),
                    "Sum of position weights = " + String.format("%.4f", sum)
                            + " — expected ≈ 1.0 (tolerance ±" + TOLERANCE + ")"));
        }
        return List.of();
    }
}
