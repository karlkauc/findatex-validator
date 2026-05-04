package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.CicCode;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;
import com.findatex.validator.validation.rules.CicApplicability;
import com.findatex.validator.validation.rules.RuleDoc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * XF-11: field 39 (Maturity date) ≥ field 7 (Reporting date) for any row whose CIC is covered by
 * the spec's applicability scope of field 39. The scope is loaded from the per-CIC qualifier text
 * in the TPT spec and currently covers CIC 1, 2, 5, 6, 7 (sub-codes 3/4/5 only — money market),
 * 8, A, B, C, D, E, F.
 */
public final class MaturityAfterReportingRule implements Rule {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final FieldSpec maturitySpec;

    public MaturityAfterReportingRule(FieldSpec maturitySpec) {
        this.maturitySpec = Objects.requireNonNull(maturitySpec, "maturitySpec");
    }

    @Override public String id() { return "XF-11/MATURITY_AFTER_REPORTING"; }

    public RuleDoc describe() {
        return new RuleDoc(
                "Field 39 (Maturity date) must not precede field 7 (Reporting date) on any row"
                        + " whose CIC matches the spec's field-39 applicability scope (CIC 1, 2,"
                        + " 5, 6, 7 sub-codes 3/4/5, 8, A, B, C, D, E, F).",
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
            if (!CicApplicability.applies(maturitySpec, row)) continue;
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
