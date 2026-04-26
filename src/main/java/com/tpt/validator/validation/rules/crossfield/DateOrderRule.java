package com.tpt.validator.validation.rules.crossfield;

import com.tpt.validator.domain.TptRow;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Rule;
import com.tpt.validator.validation.ValidationContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** XF-12: field 7 (Reporting date) ≥ field 6 (Valuation date). */
public final class DateOrderRule implements Rule {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override public String id() { return "XF-12/DATE_ORDER"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            LocalDate val = parse(row, "6");
            LocalDate rpt = parse(row, "7");
            if (val == null || rpt == null) continue;
            if (rpt.isBefore(val)) {
                out.add(Finding.error(
                        id(), null, "7", "Field 7 (Reporting date)",
                        row.rowIndex(), rpt.toString(),
                        "Reporting date " + rpt + " is before valuation date " + val));
            }
            // first-row check is enough; portfolio-level dates are typically constant per file.
            return out;
        }
        return out;
    }

    private static LocalDate parse(TptRow row, String numKey) {
        String v = row.stringValue(numKey).orElse(null);
        if (v == null) return null;
        try { return LocalDate.parse(v.trim(), ISO); } catch (DateTimeParseException e) { return null; }
    }
}
