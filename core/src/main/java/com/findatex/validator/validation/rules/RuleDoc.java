package com.findatex.validator.validation.rules;

import com.findatex.validator.validation.Severity;

import java.util.List;

/**
 * Static metadata describing what a {@link com.findatex.validator.validation.Rule} checks,
 * used by the documentation generator to produce per-template rule references without
 * executing the rule. Hand-coded cross-field rules (XF-01..XF-15, version rules) implement
 * a {@code describe()} method returning one of these; declarative rules expose their
 * {@code ConditionalRequirement} / {@code ConditionalAnyOfRequirement} / etc. directly.
 *
 * @param summary           one-sentence English description of what the rule asserts
 * @param severity          the severity emitted on failure (the worst case where multiple
 *                          severities are possible)
 * @param sourceFieldNums   {@code numKey}s of fields whose values trigger the check
 * @param targetFieldNums   {@code numKey}s of fields the rule asserts something about
 */
public record RuleDoc(
        String summary,
        Severity severity,
        List<String> sourceFieldNums,
        List<String> targetFieldNums) {

    public RuleDoc {
        sourceFieldNums = sourceFieldNums == null ? List.of() : List.copyOf(sourceFieldNums);
        targetFieldNums = targetFieldNums == null ? List.of() : List.copyOf(targetFieldNums);
    }
}
