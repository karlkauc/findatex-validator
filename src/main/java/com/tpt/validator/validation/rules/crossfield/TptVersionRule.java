package com.tpt.validator.validation.rules.crossfield;

import com.tpt.validator.domain.TptRow;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Rule;
import com.tpt.validator.validation.ValidationContext;

import java.util.List;

/** XF-15: field 1000 (TPT version) must indicate V7. */
public final class TptVersionRule implements Rule {

    @Override public String id() { return "XF-15/TPT_VERSION"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        for (TptRow row : ctx.file().rows()) {
            String v = row.stringValue("1000").orElse(null);
            if (v == null) continue;
            String norm = v.trim().toUpperCase();
            if (norm.contains("V7") || norm.contains("7.0")) return List.of();
            return List.of(Finding.error(
                    id(), null, "1000", "Field 1000 (TPT version)",
                    row.rowIndex(), v,
                    "TPT version must be V7.0 (got: " + v + ")"));
        }
        // Empty across all rows -> info (we already flag presence elsewhere if mandatory).
        return List.of(Finding.info(
                id(), null, "1000", "Field 1000 (TPT version)",
                null, null,
                "TPT version field 1000 is not present"));
    }
}
