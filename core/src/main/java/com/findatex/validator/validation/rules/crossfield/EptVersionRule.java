package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.ValidationContext;

import java.util.List;
import java.util.Objects;

/**
 * EPT analogue of {@link TptVersionRule}. Field "1" ({@code 00001_EPT_Version}) carries the
 * version token — accepted values include "V20", "V21", "V21UK" depending on the bundle.
 */
public final class EptVersionRule implements Rule {

    private final String expectedVersionToken;

    public EptVersionRule(String expectedVersionToken) {
        this.expectedVersionToken = Objects.requireNonNull(expectedVersionToken, "expectedVersionToken").trim();
    }

    public String expectedVersionToken() { return expectedVersionToken; }

    @Override public String id() { return "EPT-XF-VERSION"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        String compact = expectedVersionToken.replace(".", "").toUpperCase();
        for (TptRow row : ctx.file().rows()) {
            String v = row.stringValue("1").orElse(null);
            if (v == null) continue;
            String norm = v.trim().toUpperCase().replace(".", "");
            if (norm.contains(compact)) return List.of();
            return List.of(Finding.error(
                    id(), null, "1", "Field 1 (00001_EPT_Version)",
                    row.rowIndex(), v,
                    "EPT version must be " + expectedVersionToken + " (got: " + v + ")"));
        }
        return List.of(Finding.info(
                id(), null, "1", "Field 1 (00001_EPT_Version)",
                null, null,
                "EPT version field 1 (00001_EPT_Version) is not present"));
    }
}
