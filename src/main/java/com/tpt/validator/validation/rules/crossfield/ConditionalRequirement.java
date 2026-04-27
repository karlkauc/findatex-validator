package com.tpt.validator.validation.rules.crossfield;

import com.tpt.validator.validation.Severity;

/**
 * Declarative encoding of a single cross-field "if X then Y must be present"
 * rule pulled from the TPT V7 spec, e.g.
 * <pre>
 *   if field 48 = "1"  ⇒  field 47 must be present
 *   if field 42 ∈ {"Cal", "Put"}  ⇒  field 43 must be present
 *   if field 138 ∈ {"1","2","3"} ⇒  field 139 must be present
 * </pre>
 *
 * <p>One requirement maps to one {@link ConditionalFieldPresenceRule} instance
 * via {@link com.tpt.validator.validation.RuleRegistry}.
 *
 * @param ruleId               unique rule identifier for finding output (e.g. {@code "XF-20/ISSUER_LEI_PRESENT"})
 * @param sourceFieldNum       NUM_DATA key of the trigger field (e.g. {@code "48"})
 * @param condition            predicate evaluated against the source value
 * @param targetFieldNum       NUM_DATA key of the field that must be present when {@code condition} holds
 * @param severityWhenMissing  finding severity emitted when the condition holds but target is empty
 */
public record ConditionalRequirement(
        String ruleId,
        String sourceFieldNum,
        FieldPredicate condition,
        String targetFieldNum,
        Severity severityWhenMissing) {
}
