package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.validation.Severity;

import java.util.List;

/**
 * Cross-field "if any of {sources} satisfies the predicate, then target must
 * be present" requirement. Used by EET to encode spec text like
 * <em>"Conditional to 20040 or 20050 set to 8 or 9"</em> on the PCDFP fields
 * (NUMs 35/36) — a single finding per row even when multiple sources match.
 *
 * <p>Mirror of {@link ConditionalAnyOfRequirement} on the source side: there
 * the target list disjuncts ("at least one of"); here the source list
 * disjuncts ("at least one source triggers").
 *
 * @param ruleId               unique rule identifier emitted with each finding
 * @param sourceFieldNums      NUM keys; the condition is satisfied when the
 *                             predicate holds on any one of these
 * @param condition            predicate evaluated against each source value
 * @param targetFieldNum       NUM key of the field that must be present when
 *                             at least one source satisfies the predicate
 * @param severityWhenMissing  severity emitted when target is blank while any
 *                             source satisfies the predicate
 */
public record ConditionalAnySourceRequirement(
        String ruleId,
        List<String> sourceFieldNums,
        FieldPredicate condition,
        String targetFieldNum,
        Severity severityWhenMissing) {

    public ConditionalAnySourceRequirement {
        sourceFieldNums = List.copyOf(sourceFieldNums);
    }
}
