package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;
import com.findatex.validator.validation.rules.RuleDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * XF-10: Field 32 = Floating ⇒ fields 34..37 mandatory; Fixed ⇒ field 33 mandatory.
 * Variant-tolerant: matches "Floating", "Float", "Variable", "Fixed".
 */
public final class InterestRateTypeRule implements Rule {

    /** TPT V7: reference id, id type, index name and margin. */
    public static final List<String> FLOATING_FIELDS_V7 = List.of("34", "35", "36", "37");
    /** TPT V8: fields 35 and 36 are "N/A, must be left empty", only reference id and margin remain. */
    public static final List<String> FLOATING_FIELDS_V8 = List.of("34", "37");

    private final List<String> requiredWhenFloating;

    /** V7 behaviour (fields 34..37). */
    public InterestRateTypeRule() {
        this(FLOATING_FIELDS_V7);
    }

    public InterestRateTypeRule(List<String> requiredWhenFloating) {
        this.requiredWhenFloating = List.copyOf(requiredWhenFloating);
    }

    public List<String> requiredWhenFloating() { return requiredWhenFloating; }

    @Override public String id() { return "XF-10/INTEREST_RATE_TYPE"; }

    public RuleDoc describe() {
        List<String> targets = new ArrayList<>();
        targets.add("33");
        targets.addAll(requiredWhenFloating);
        return new RuleDoc(
                "When field 32 (interest-rate type) is Floating/Variable, fields "
                        + String.join(", ", requiredWhenFloating) + " are mandatory;"
                        + " when Fixed, field 33 (Coupon rate) is mandatory.",
                Severity.ERROR,
                List.of("32"),
                targets);
    }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            String t = row.stringValue("32").orElse("").toLowerCase(Locale.ROOT).trim();
            if (t.isEmpty()) continue;

            if (t.startsWith("float") || t.startsWith("variable")) {
                for (String fk : requiredWhenFloating) {
                    if (row.stringValue(fk).isEmpty()) {
                        out.add(Finding.error(
                                id(), null, fk, "Field " + fk,
                                row.rowIndex(), null,
                                "Field " + fk + " is mandatory when field 32 = Floating/Variable"));
                    }
                }
            } else if (t.startsWith("fix")) {
                if (row.stringValue("33").isEmpty()) {
                    out.add(Finding.error(
                            id(), null, "33", "Field 33 (Coupon rate)",
                            row.rowIndex(), null,
                            "Field 33 is mandatory when field 32 = Fixed"));
                }
            }
        }
        return out;
    }
}
