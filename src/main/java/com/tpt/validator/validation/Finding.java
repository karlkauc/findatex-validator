package com.tpt.validator.validation;

import com.tpt.validator.spec.Profile;

public record Finding(
        Severity severity,
        String ruleId,
        Profile profile,            // null = applies to all
        String fieldNum,            // null for portfolio-level / cross-field
        String fieldName,           // human-readable
        Integer rowIndex,           // null for portfolio/global
        String value,               // raw cell value (or null)
        String message) {

    public static Finding error(String ruleId, Profile p, String fieldNum, String fieldName,
                                Integer rowIdx, String value, String message) {
        return new Finding(Severity.ERROR, ruleId, p, fieldNum, fieldName, rowIdx, value, message);
    }

    public static Finding warn(String ruleId, Profile p, String fieldNum, String fieldName,
                               Integer rowIdx, String value, String message) {
        return new Finding(Severity.WARNING, ruleId, p, fieldNum, fieldName, rowIdx, value, message);
    }

    public static Finding info(String ruleId, Profile p, String fieldNum, String fieldName,
                               Integer rowIdx, String value, String message) {
        return new Finding(Severity.INFO, ruleId, p, fieldNum, fieldName, rowIdx, value, message);
    }
}
