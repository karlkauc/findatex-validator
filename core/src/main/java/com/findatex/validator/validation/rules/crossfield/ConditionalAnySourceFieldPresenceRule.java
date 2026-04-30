package com.findatex.validator.validation.rules.crossfield;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-field rule that emits a single finding per row whenever <em>any</em>
 * of the configured source fields satisfies the predicate while the target
 * is empty. Configured via {@link ConditionalAnySourceRequirement}.
 */
public final class ConditionalAnySourceFieldPresenceRule implements Rule {

    private final ConditionalAnySourceRequirement req;

    public ConditionalAnySourceFieldPresenceRule(ConditionalAnySourceRequirement req) {
        this.req = req;
    }

    @Override
    public String id() { return req.ruleId(); }

    public ConditionalAnySourceRequirement requirement() { return req; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        String fieldName = ctx.catalog().byNumKey(req.targetFieldNum())
                .map(FieldSpec::numData)
                .orElse("Field " + req.targetFieldNum());

        for (TptRow row : ctx.file().rows()) {
            String matchedSource = null;
            for (String sourceNum : req.sourceFieldNums()) {
                String sv = row.stringValue(sourceNum).orElse(null);
                if (req.condition().test(sv)) {
                    matchedSource = sourceNum;
                    break;
                }
            }
            if (matchedSource == null) continue;
            if (row.stringValue(req.targetFieldNum()).isPresent()) continue;

            String message = String.format(
                    "Field %s (%s) is required because field %s %s",
                    req.targetFieldNum(), fieldName,
                    matchedSource, req.condition().describe());
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
