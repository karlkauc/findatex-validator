package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.ValidationContext;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XF-15: field 1000 (TPT version) must indicate the expected template version. Parametrised
 * over {@code expectedVersion} so the same rule type works for V7.0 ("V7" / "7.0") and V6.0
 * ("V6" / "6.0") without code duplication.
 */
public final class TptVersionRule implements Rule {

    private static final Pattern VERSION_PATTERN = Pattern.compile("V?(\\d+)(?:\\.(\\d+))?", Pattern.CASE_INSENSITIVE);

    private final String expectedVersion;
    private final String expectedMajorToken;
    private final String expectedNumericToken;

    public TptVersionRule(String expectedVersion) {
        this.expectedVersion = Objects.requireNonNull(expectedVersion, "expectedVersion").trim();
        Matcher m = VERSION_PATTERN.matcher(this.expectedVersion);
        if (!m.find()) {
            throw new IllegalArgumentException("Cannot parse version token from: " + expectedVersion);
        }
        String major = m.group(1);
        String minor = m.group(2);
        this.expectedMajorToken = ("V" + major).toUpperCase();
        this.expectedNumericToken = minor == null ? major : major + "." + minor;
    }

    @Override public String id() { return "XF-15/TPT_VERSION"; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        for (TptRow row : ctx.file().rows()) {
            String v = row.stringValue("1000").orElse(null);
            if (v == null) continue;
            String norm = v.trim().toUpperCase();
            if (norm.contains(expectedMajorToken) || norm.contains(expectedNumericToken)) return List.of();
            return List.of(Finding.error(
                    id(), null, "1000", "Field 1000 (TPT version)",
                    row.rowIndex(), v,
                    "TPT version must be " + expectedVersion + " (got: " + v + ")"));
        }
        return List.of(Finding.info(
                id(), null, "1000", "Field 1000 (TPT version)",
                null, null,
                "TPT version field 1000 is not present"));
    }
}
