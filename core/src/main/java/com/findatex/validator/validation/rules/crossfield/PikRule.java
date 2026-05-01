package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.CicCode;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;
import com.findatex.validator.validation.rules.RuleDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * XF-13: field 146 (PIK) — guidance from PIK guidelines 240913.xlsx.
 * Cases 1..4 enforce different patterns of fields 32..38 and 41.
 * Per the spec comment, value 0 means "no PIK" and applies only to bonds (CIC xx2x) and loans (CIC xx8x).
 */
public final class PikRule implements Rule {

    private static final Set<String> ALLOWED = Set.of("0", "1", "2", "3", "4");

    @Override public String id() { return "XF-13/PIK"; }

    public RuleDoc describe() {
        return new RuleDoc(
                "Field 146 (PIK) must be one of {0, 1, 2, 3, 4} and is meaningful only for bonds"
                        + " (CIC xx2x) and loans (CIC xx8x). Each PIK case mandates a specific"
                        + " subset of fields 32, 33, 38, 39, 40, 41 per the PIK guidelines.",
                Severity.ERROR,
                List.of("146"),
                List.of("32", "33", "38", "39", "40", "41", "146"));
    }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            String pik = row.stringValue("146").orElse(null);
            if (pik == null) continue;
            String norm = pik.trim();
            if (norm.endsWith(".0")) norm = norm.substring(0, norm.length() - 2);
            if (!ALLOWED.contains(norm)) {
                out.add(Finding.error(
                        id(), null, "146", "Field 146 (PIK)",
                        row.rowIndex(), pik,
                        "PIK code must be one of {0,1,2,3,4} per PIK guidelines"));
                continue;
            }
            CicCode cic = row.cic().orElse(null);
            if (cic != null) {
                String cat = cic.categoryDigit();
                if (!cat.equals("2") && !cat.equals("8")) {
                    out.add(Finding.warn(
                            id(), null, "146", "Field 146 (PIK)",
                            row.rowIndex(), pik,
                            "PIK is meaningful only for bonds (CIC xx2x) and loans (CIC xx8x)"));
                }
            }
            // Case-specific field expectations (per PIK guidelines):
            switch (norm) {
                case "1" -> {
                    // No cash coupon; existing PIK in redemption rate; coupon fields describe the PIK.
                    // Expect 32 (rate type), 38 (frequency), 41 (redemption rate), 39 (maturity), 40 (redemption type) present.
                    requirePresent(out, row, "1", "32");
                    requirePresent(out, row, "1", "38");
                    requirePresent(out, row, "1", "39");
                    requirePresent(out, row, "1", "40");
                    requirePresent(out, row, "1", "41");
                }
                case "2" -> {
                    // PIK is in redemption rate but coupon fields describe the actual cash coupon.
                    requirePresent(out, row, "2", "33");
                    requirePresent(out, row, "2", "41");
                }
                case "3" -> {
                    requirePresent(out, row, "3", "33");
                    requirePresent(out, row, "3", "38");
                }
                default -> { /* 0 or 4: no extra constraints */ }
            }
        }
        return out;
    }

    private static void requirePresent(List<Finding> out, TptRow row, String pikCase, String numKey) {
        if (row.stringValue(numKey).isEmpty()) {
            out.add(Finding.warn(
                    "XF-13/PIK_CASE_" + pikCase + "_FIELD_" + numKey,
                    null, numKey, "Field " + numKey,
                    row.rowIndex(), null,
                    "PIK case " + pikCase + " expects field " + numKey + " to be filled (per PIK guidelines)"));
        }
    }
}
