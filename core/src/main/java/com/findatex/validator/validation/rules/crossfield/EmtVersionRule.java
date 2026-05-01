package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.ValidationContext;

import java.util.List;
import java.util.Objects;

/** EMT analogue of {@link TptVersionRule}. Field "1" ({@code 00001_EMT_Version}) carries the version. */
public final class EmtVersionRule implements Rule {

    private final String expectedVersion;

    public EmtVersionRule(String expectedVersion) {
        this.expectedVersion = Objects.requireNonNull(expectedVersion, "expectedVersion").trim();
    }

    public String expectedVersionToken() { return expectedVersion; }

    @Override public String id() { return "EMT-XF-VERSION"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        for (TptRow row : ctx.file().rows()) {
            String v = row.stringValue("1").orElse(null);
            if (v == null) continue;
            String norm = v.trim().toUpperCase();
            if (norm.contains(expectedVersion.toUpperCase())) return List.of();
            return List.of(Finding.error(
                    id(), null, "1", "Field 1 (00001_EMT_Version)",
                    row.rowIndex(), v,
                    "EMT version must be " + expectedVersion + " (got: " + v + ")"));
        }
        return List.of(Finding.info(
                id(), null, "1", "Field 1 (00001_EMT_Version)",
                null, null,
                "EMT version field 1 (00001_EMT_Version) is not present"));
    }
}
