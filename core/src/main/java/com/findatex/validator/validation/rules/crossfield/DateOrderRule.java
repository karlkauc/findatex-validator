package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.FundGroup;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;
import com.findatex.validator.validation.rules.RuleDoc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** XF-12: field 7 (Reporting date) ≥ field 6 (Valuation date). */
public final class DateOrderRule implements Rule {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override public String id() { return "XF-12/DATE_ORDER"; }

    public RuleDoc describe() {
        return new RuleDoc(
                "Field 7 (Reporting date) must not precede field 6 (Valuation date).",
                Severity.ERROR,
                List.of("6", "7"),
                List.of("7"));
    }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (FundGroup g : ctx.fundGroups()) {
            for (TptRow row : g.rows()) {
                LocalDate val = parse(row, "6");
                LocalDate rpt = parse(row, "7");
                if (val == null || rpt == null) continue;
                if (rpt.isBefore(val)) {
                    out.add(Finding.error(
                            id(), null, "7", "Field 7 (Reporting date)",
                            row.rowIndex(), rpt.toString(),
                            "Reporting date " + rpt + " is before valuation date " + val));
                }
                // first non-empty (6,7) row of each fund is enough.
                break;
            }
        }
        return out;
    }

    private static LocalDate parse(TptRow row, String numKey) {
        String v = row.stringValue(numKey).orElse(null);
        if (v == null) return null;
        try { return LocalDate.parse(v.trim(), ISO); } catch (DateTimeParseException e) { return null; }
    }
}
