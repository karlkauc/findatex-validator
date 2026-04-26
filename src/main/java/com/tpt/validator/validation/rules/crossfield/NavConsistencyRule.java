package com.tpt.validator.validation.rules.crossfield;

import com.tpt.validator.domain.TptRow;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Rule;
import com.tpt.validator.validation.ValidationContext;

import java.util.List;

/** XF-06: field 5 ≈ field 8 × field 8b within precision tolerance. */
public final class NavConsistencyRule implements Rule {

    private static final double REL_TOLERANCE = 0.01; // 1 % relative

    @Override public String id() { return "XF-06/NAV_PRICE_SHARES"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        Double tna  = pick(ctx, "5");
        Double price = pick(ctx, "8");
        Double shares = pick(ctx, "8b");
        if (tna == null || price == null || shares == null || tna == 0) return List.of();
        double computed = price * shares;
        double rel = Math.abs(computed - tna) / Math.abs(tna);
        if (rel > REL_TOLERANCE) {
            return List.of(Finding.warn(
                    id(), null, "5", "Field 5 (TotalNetAssets)",
                    null, String.format("%.4f", tna),
                    "TotalNetAssets " + String.format("%.4f", tna)
                            + " differs from SharePrice × Shares = "
                            + String.format("%.4f", computed)
                            + " (relative tolerance " + REL_TOLERANCE + ")"));
        }
        return List.of();
    }

    private static Double pick(ValidationContext ctx, String numKey) {
        for (TptRow row : ctx.file().rows()) {
            String v = row.stringValue(numKey).orElse(null);
            if (v != null) {
                try { return Double.parseDouble(v.replace(",", ".")); } catch (NumberFormatException ignore) {}
            }
        }
        return null;
    }
}
