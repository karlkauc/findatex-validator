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

/** XF-14: field 67 (underlying CIC) mandatory iff main CIC ∈ {2, A, B, C, D, F}. */
public final class UnderlyingCicRule implements Rule {

    private static final Set<String> NEEDS_UNDERLYING = Set.of("2", "A", "B", "C", "D", "F");

    @Override public String id() { return "XF-14/UNDERLYING_CIC"; }

    public RuleDoc describe() {
        return new RuleDoc(
                "Field 67 (Underlying CIC) is mandatory when the main CIC category is one of"
                        + " {2, A, B, C, D, F} (instruments that have an economic underlying).",
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
            if (!NEEDS_UNDERLYING.contains(cic.categoryDigit())) continue;
            if (row.stringValue("67").isEmpty()) {
                out.add(Finding.error(
                        id(), null, "67", "Field 67 (CIC of the underlying asset)",
                        row.rowIndex(), null,
                        "Underlying CIC is mandatory for instruments of CIC " + cic.categoryDigit()));
            }
        }
        return out;
    }
}
