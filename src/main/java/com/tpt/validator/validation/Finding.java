package com.tpt.validator.validation;

import com.tpt.validator.template.api.ProfileKey;

public record Finding(
        Severity severity,
        String ruleId,
        ProfileKey profile,         // null = applies to all
        String fieldNum,            // null for portfolio-level / cross-field
        String fieldName,           // human-readable
        Integer rowIndex,           // null for portfolio/global
        String value,               // raw cell value (or null)
        String message,
        FindingContext context) {   // attached by FindingEnricher; null until enrichment

    public Finding(Severity severity, String ruleId, ProfileKey profile, String fieldNum,
                   String fieldName, Integer rowIndex, String value, String message) {
        this(severity, ruleId, profile, fieldNum, fieldName, rowIndex, value, message, null);
    }

    public static Finding error(String ruleId, ProfileKey p, String fieldNum, String fieldName,
                                Integer rowIdx, String value, String message) {
        return new Finding(Severity.ERROR, ruleId, p, fieldNum, fieldName, rowIdx, value, message);
    }

    public static Finding warn(String ruleId, ProfileKey p, String fieldNum, String fieldName,
                               Integer rowIdx, String value, String message) {
        return new Finding(Severity.WARNING, ruleId, p, fieldNum, fieldName, rowIdx, value, message);
    }

    public static Finding info(String ruleId, ProfileKey p, String fieldNum, String fieldName,
                               Integer rowIdx, String value, String message) {
        return new Finding(Severity.INFO, ruleId, p, fieldNum, fieldName, rowIdx, value, message);
    }

    /** Returns a new Finding with the given context attached (immutable). */
    public Finding withContext(FindingContext ctx) {
        return new Finding(severity, ruleId, profile, fieldNum, fieldName, rowIndex, value, message, ctx);
    }
}
