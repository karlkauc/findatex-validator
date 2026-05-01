package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.CicCode;
import com.findatex.validator.domain.FundGroup;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;
import com.findatex.validator.validation.rules.RuleDoc;

import java.util.ArrayList;
import java.util.List;

/** XF-05: field 9 (CashPercentage) ≈ Σ MarketValuePC of CIC xx7x / TotalNetAssets. */
public final class CashPercentageRule implements Rule {

    private static final double TOLERANCE = 0.05; // ±5 %

    @Override public String id() { return "XF-05/CASH_PERCENTAGE"; }

    public RuleDoc describe() {
        return new RuleDoc(
                "Declared cash ratio (field 9) must match Σ MarketValuePC of CIC xx7x positions"
                        + " divided by TotalNetAssets, within ±" + TOLERANCE + ".",
                Severity.WARNING,
                List.of("5", "24"),
                List.of("9"));
    }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (FundGroup g : ctx.fundGroups()) {
            Double declaredCashPct = firstInGroup(g, "9");
            Double totalNet = firstInGroup(g, "5");
            if (declaredCashPct == null || totalNet == null || totalNet == 0) continue;

            double cashSum = 0;
            for (TptRow row : g.rows()) {
                CicCode cic = row.cic().orElse(null);
                if (cic == null || !"7".equals(cic.categoryDigit())) continue;
                String mv = row.stringValue("24").orElse(null);
                if (mv == null) continue;
                try { cashSum += Double.parseDouble(mv.replace(",", ".")); } catch (NumberFormatException ignore) {}
            }
            double computed = cashSum / totalNet;
            if (Math.abs(computed - declaredCashPct) > TOLERANCE) {
                out.add(Finding.warn(
                        id(), null, "9", "Field 9 (Cash percentage)",
                        g.firstRowIndex(), String.format("%.4f", declaredCashPct),
                        "Declared cash ratio " + String.format("%.4f", declaredCashPct)
                                + " differs from Σ(MarketValuePC of CIC xx7x)/TotalNetAssets = "
                                + String.format("%.4f", computed) + " (tolerance ±" + TOLERANCE + ")"));
            }
        }
        return out;
    }

    private static Double firstInGroup(FundGroup g, String numKey) {
        for (TptRow row : g.rows()) {
            String v = row.stringValue(numKey).orElse(null);
            if (v != null) {
                try { return Double.parseDouble(v.replace(",", ".")); } catch (NumberFormatException ignore) { return null; }
            }
        }
        return null;
    }
}
