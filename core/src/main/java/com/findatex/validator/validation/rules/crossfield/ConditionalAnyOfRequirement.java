package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.validation.Severity;

import java.util.List;

/**
 * Cross-field "if X then at least one of {targets} must be present"
 * requirement. Used by EET to encode the soft taxonomy-attribution
 * rule: when the Art-8 minimum (NUM=41) is reported, at least one of
 * the sub-attribution Y/N flags (NUMs 42/43/44) must indicate which
 * category it covers — without imposing a sum-check (which the spec
 * does not actually mandate).
 *
 * @param ruleId                  unique rule identifier emitted with each finding
 * @param sourceFieldNum          NUM key of the trigger field
 * @param condition               predicate evaluated against the source value
 * @param targetFieldNums         NUM keys; the requirement is satisfied when
 *                                at least one of these is non-blank
 * @param severityWhenAllMissing  severity emitted when ALL targets are blank
 *                                while the source predicate holds
 */
public record ConditionalAnyOfRequirement(
        String ruleId,
        String sourceFieldNum,
        FieldPredicate condition,
        List<String> targetFieldNums,
        Severity severityWhenAllMissing) {

    public ConditionalAnyOfRequirement {
        targetFieldNums = List.copyOf(targetFieldNums);
    }
}
