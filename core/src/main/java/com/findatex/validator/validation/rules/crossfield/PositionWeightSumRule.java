package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;
import com.findatex.validator.validation.rules.RuleDoc;

import java.util.List;

/** XF-04: Σ field 26 (PositionWeight) ≈ 1 within tolerance. */
public final class PositionWeightSumRule implements Rule {

    private static final double TOLERANCE = 0.02; // ±2 % to allow rounding/derivative weights

    @Override public String id() { return "XF-04/POSITION_WEIGHT_SUM"; }

    public RuleDoc describe() {
        return new RuleDoc(
                "Σ field 26 (Position weight) across all positions must be ≈ 1.0 within ±" + TOLERANCE + ".",
                Severity.WARNING,
                List.of("26"),
                List.of("26"));
    }

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
