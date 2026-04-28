package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;

/** XF-09: field 141 mandatory iff field 140 is filled (custodian code/type pair). */
public final class CustodianPairRule implements Rule {

    @Override public String id() { return "XF-09/CUSTODIAN_PAIR"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            boolean has140 = row.stringValue("140").isPresent();
            boolean has141 = row.stringValue("141").isPresent();
            if (has140 && !has141) {
                out.add(Finding.error(
                        id(), null, "141", "Field 141 (Type of custodian identification code)",
                        row.rowIndex(), null,
                        "Field 141 is mandatory whenever field 140 is filled"));
            }
            if (!has140 && has141) {
                out.add(Finding.warn(
                        id(), null, "140", "Field 140 (Custodian identification code)",
                        row.rowIndex(), null,
                        "Field 140 is missing but its type indicator (141) is set"));
            }
        }
        return out;
    }
}
