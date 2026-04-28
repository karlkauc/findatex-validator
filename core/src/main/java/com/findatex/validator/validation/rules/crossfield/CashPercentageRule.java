package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.CicCode;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.ValidationContext;

import java.util.List;

/** XF-05: field 9 (CashPercentage) ≈ Σ MarketValuePC of CIC xx7x / TotalNetAssets. */
public final class CashPercentageRule implements Rule {

    private static final double TOLERANCE = 0.05; // ±5 %

    @Override public String id() { return "XF-05/CASH_PERCENTAGE"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        Double declaredCashPct = first(ctx, "9");
        Double totalNet = first(ctx, "5");
        if (declaredCashPct == null || totalNet == null || totalNet == 0) return List.of();

        double cashSum = 0;
        for (TptRow row : ctx.file().rows()) {
            CicCode cic = row.cic().orElse(null);
            if (cic == null || !"7".equals(cic.categoryDigit())) continue;
            String mv = row.stringValue("24").orElse(null);
            if (mv == null) continue;
            try { cashSum += Double.parseDouble(mv.replace(",", ".")); } catch (NumberFormatException ignore) {}
        }
        double computed = cashSum / totalNet;
        if (Math.abs(computed - declaredCashPct) > TOLERANCE) {
            return List.of(Finding.warn(
                    id(), null, "9", "Field 9 (Cash percentage)",
                    null, String.format("%.4f", declaredCashPct),
                    "Declared cash ratio " + String.format("%.4f", declaredCashPct)
                            + " differs from Σ(MarketValuePC of CIC xx7x)/TotalNetAssets = "
                            + String.format("%.4f", computed) + " (tolerance ±" + TOLERANCE + ")"));
        }
        return List.of();
    }

    private static Double first(ValidationContext ctx, String numKey) {
        for (TptRow row : ctx.file().rows()) {
            String v = row.stringValue(numKey).orElse(null);
            if (v != null) {
                try { return Double.parseDouble(v.replace(",", ".")); } catch (NumberFormatException ignore) { return null; }
            }
        }
        return null;
    }
}
