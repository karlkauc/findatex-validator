package com.tpt.validator.validation.rules.crossfield;

import com.tpt.validator.domain.TptRow;
import com.tpt.validator.spec.FieldSpec;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Rule;
import com.tpt.validator.validation.Severity;
import com.tpt.validator.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic cross-field rule that emits a finding whenever the source field's
 * predicate holds on a row but the target field is empty. Configured via a
 * {@link ConditionalRequirement}; the registry instantiates one per requirement.
 */
public final class ConditionalFieldPresenceRule implements Rule {

    private final ConditionalRequirement req;

    public ConditionalFieldPresenceRule(ConditionalRequirement req) {
        this.req = req;
    }

    @Override
    public String id() { return req.ruleId(); }

    public ConditionalRequirement requirement() { return req; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        String fieldName = ctx.catalog().byNumKey(req.targetFieldNum())
                .map(FieldSpec::numData)
                .orElse("Field " + req.targetFieldNum());

        for (TptRow row : ctx.file().rows()) {
            String sourceVal = row.stringValue(req.sourceFieldNum()).orElse(null);
            if (!req.condition().test(sourceVal)) continue;
            if (row.stringValue(req.targetFieldNum()).isPresent()) continue;

            String message = String.format(
                    "Field %s (%s) is required because field %s %s",
                    req.targetFieldNum(),
                    fieldName,
                    req.sourceFieldNum(),
                    req.condition().describe());
            out.add(emit(req.ruleId(), req.severityWhenMissing(),
                    req.targetFieldNum(), fieldName, row.rowIndex(), message));
        }
        return out;
    }

    private static Finding emit(String ruleId, Severity sev, String fieldNum,
                                String fieldName, int rowIndex, String message) {
        return switch (sev) {
            case ERROR   -> Finding.error(ruleId, null, fieldNum, fieldName, rowIndex, null, message);
            case WARNING -> Finding.warn (ruleId, null, fieldNum, fieldName, rowIndex, null, message);
            case INFO    -> Finding.info (ruleId, null, fieldNum, fieldName, rowIndex, null, message);
        };
    }
}
