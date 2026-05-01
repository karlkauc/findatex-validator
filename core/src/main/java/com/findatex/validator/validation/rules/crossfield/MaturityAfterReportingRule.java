package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.CicCode;
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

/** XF-11: field 39 (Maturity date) >= field 7 (Reporting date) for active interest-rate instruments. */
public final class MaturityAfterReportingRule implements Rule {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override public String id() { return "XF-11/MATURITY_AFTER_REPORTING"; }

    public RuleDoc describe() {
        return new RuleDoc(
                "For dated/interest-rate instruments (CIC categories 1, 2, 5, 6, 8), field 39"
                        + " (Maturity date) must not precede field 7 (Reporting date).",
                Severity.WARNING,
                List.of("7"),
                List.of("39"));
    }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        // Reporting date is portfolio-level — pick first non-empty.
        LocalDate reporting = null;
        for (TptRow row : ctx.file().rows()) {
            String v = row.stringValue("7").orElse(null);
            if (v == null) continue;
            try { reporting = LocalDate.parse(v.trim(), ISO); break; } catch (DateTimeParseException ignore) {}
        }
        if (reporting == null) return List.of();

        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            CicCode cic = row.cic().orElse(null);
            if (cic == null) continue;
            // Only interest-rate / dated instruments: CIC 1, 2, 5, 6, 8.
            String cat = cic.categoryDigit();
            if (!cat.equals("1") && !cat.equals("2") && !cat.equals("5") && !cat.equals("6") && !cat.equals("8")) continue;
            String v = row.stringValue("39").orElse(null);
            if (v == null) continue;
            LocalDate maturity;
            try { maturity = LocalDate.parse(v.trim(), ISO); } catch (DateTimeParseException e) { continue; }
            if (maturity.isBefore(reporting)) {
                out.add(Finding.warn(
                        id(), null, "39", "Field 39 (Maturity date)",
                        row.rowIndex(), maturity.toString(),
                        "Instrument maturity " + maturity + " precedes reporting date " + reporting));
            }
        }
        return out;
    }
}
