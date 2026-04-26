package com.tpt.validator.validation.rules.crossfield;

import com.tpt.validator.domain.TptRow;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Rule;
import com.tpt.validator.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;

/** XF-01: field 11 = "Y" => fields 97..105b mandatory. */
public final class CompleteScrDeliveryRule implements Rule {

    private static final String[] SCR_FIELDS = {
            "97", "98", "99", "100", "101", "102", "103", "104", "105", "105a", "105b"
    };

    @Override public String id() { return "XF-01/COMPLETE_SCR_DELIVERY"; }

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
