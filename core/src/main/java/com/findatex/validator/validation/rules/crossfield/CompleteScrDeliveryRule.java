package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;
import com.findatex.validator.validation.rules.RuleDoc;

import java.util.ArrayList;
import java.util.List;

/** XF-01: field 11 = "Y" => fields 97..105b mandatory. */
public final class CompleteScrDeliveryRule implements Rule {

    private static final String[] SCR_FIELDS = {
            "97", "98", "99", "100", "101", "102", "103", "104", "105", "105a", "105b"
    };

    @Override public String id() { return "XF-01/COMPLETE_SCR_DELIVERY"; }

    public RuleDoc describe() {
        return new RuleDoc(
                "When field 11 (CompleteSCRDelivery) is \"Y\", every SCR contribution field"
                        + " 97..105b must be populated.",
                Severity.ERROR,
                List.of("11"),
                List.of(SCR_FIELDS));
    }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            String f11 = row.stringValue("11").orElse("").trim().toUpperCase();
            if (!f11.equals("Y")) continue;
            for (String fk : SCR_FIELDS) {
                if (row.stringValue(fk).isEmpty()) {
                    out.add(Finding.error(
                            id(), null, fk, "SCR contribution field " + fk,
                            row.rowIndex(), null,
                            "Field 11 (CompleteSCRDelivery) = Y but field " + fk + " is missing"));
                }
            }
        }
        return out;
    }
}
