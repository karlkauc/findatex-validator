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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * XF-14: field 67 (Underlying CIC) is mandatory for any row whose CIC matches the spec's
 * applicability scope for field 67. The scope is loaded from the per-CIC qualifier text in the
 * TPT spec sheet (e.g. {@code "x for 22"} on CIC2 → only CIC <code>xx22</code>; {@code "x for D4, D5"}
 * on CICD → only CIC <code>xxD4</code> and <code>xxD5</code>; plain {@code "x"} on CICA/B/C/F
 * → all sub-codes).
 */
public final class UnderlyingCicRule implements Rule {

    private final FieldSpec underlyingCicSpec;

    public UnderlyingCicRule(FieldSpec underlyingCicSpec) {
        this.underlyingCicSpec = Objects.requireNonNull(underlyingCicSpec, "underlyingCicSpec");
    }

    @Override public String id() { return "XF-14/UNDERLYING_CIC"; }

    public RuleDoc describe() {
        return new RuleDoc(
                "Field 67 (Underlying CIC) is mandatory whenever the spec's CIC applicability"
                        + " scope for field 67 covers the row's CIC — i.e. CIC 22, A, B, C, D4,"
                        + " D5, F. Other sub-codes within CIC 2 and CIC D are exempt.",
                Severity.ERROR,
                List.of("12"),
                List.of("67"));
    }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            CicCode cic = row.cic().orElse(null);
            if (cic == null) continue;
            if (!CicApplicability.applies(underlyingCicSpec, row)) continue;
            if (row.stringValue("67").isEmpty()) {
                out.add(Finding.error(
                        id(), null, "67", "Field 67 (CIC of the underlying asset)",
                        row.rowIndex(), null,
                        "Underlying CIC is mandatory for instruments of CIC "
                                + cic.categoryDigit() + cic.subcategory()));
            }
        }
        return out;
    }
}
