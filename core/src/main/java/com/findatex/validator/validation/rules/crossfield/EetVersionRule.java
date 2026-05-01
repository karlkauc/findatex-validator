package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.ValidationContext;

import java.util.List;
import java.util.Objects;

/**
 * Mirrors {@link TptVersionRule} for EET: field "1" (NUM 1, {@code 00010_EET_Version}) carries
 * the spec version the file was produced against. Parametrised so V1.1.3 and V1.1.2 reuse the
 * same rule type.
 */
public final class EetVersionRule implements Rule {

    private final String expectedVersion;

    public EetVersionRule(String expectedVersion) {
        this.expectedVersion = Objects.requireNonNull(expectedVersion, "expectedVersion").trim();
    }

    public String expectedVersionToken() { return expectedVersion; }

    @Override public String id() { return "EET-XF-VERSION"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        for (TptRow row : ctx.file().rows()) {
            String v = row.stringValue("1").orElse(null);
            if (v == null) continue;
            String norm = v.trim().toUpperCase();
            if (norm.contains(expectedVersion.toUpperCase())) return List.of();
            return List.of(Finding.error(
                    id(), null, "1", "Field 1 (00010_EET_Version)",
                    row.rowIndex(), v,
                    "EET version must be " + expectedVersion + " (got: " + v + ")"));
        }
        return List.of(Finding.info(
                id(), null, "1", "Field 1 (00010_EET_Version)",
                null, null,
                "EET version field 1 (00010_EET_Version) is not present"));
    }
}
