package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.FundGroup;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;
import com.findatex.validator.validation.rules.RuleDoc;

import java.util.ArrayList;
import java.util.List;

/** XF-06: field 5 ≈ field 8 × field 8b within precision tolerance. */
public final class NavConsistencyRule implements Rule {

    private static final double REL_TOLERANCE = 0.01; // 1 % relative

    @Override public String id() { return "XF-06/NAV_PRICE_SHARES"; }

    public RuleDoc describe() {
        return new RuleDoc(
                "TotalNetAssets (field 5) must match SharePrice (field 8) × Shares (field 8b)"
                        + " within ±" + REL_TOLERANCE + " relative tolerance.",
                Severity.WARNING,
                List.of("8", "8b"),
                List.of("5"));
    }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (FundGroup g : ctx.fundGroups()) {
            Double tna    = pick(g, "5");
            Double price  = pick(g, "8");
            Double shares = pick(g, "8b");
            if (tna == null || price == null || shares == null || tna == 0) continue;
            double computed = price * shares;
            double rel = Math.abs(computed - tna) / Math.abs(tna);
            if (rel > REL_TOLERANCE) {
                out.add(Finding.warn(
                        id(), null, "5", "Field 5 (TotalNetAssets)",
                        g.firstRowIndex(), String.format("%.4f", tna),
                        "TotalNetAssets " + String.format("%.4f", tna)
                                + " differs from SharePrice × Shares = "
                                + String.format("%.4f", computed)
                                + " (relative tolerance " + REL_TOLERANCE + ")"));
            }
        }
        return out;
    }

    private static Double pick(FundGroup g, String numKey) {
        for (TptRow row : g.rows()) {
            String v = row.stringValue(numKey).orElse(null);
            if (v != null) {
                try { return Double.parseDouble(v.replace(",", ".")); } catch (NumberFormatException ignore) {}
            }
        }
        return null;
    }
}
