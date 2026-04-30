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
 * Inverse of {@link ConditionalFieldPresenceRule}: emits a finding whenever
 * the source field's predicate holds on a row AND the target field IS
 * populated. Used for "if X = Y then Z must NOT be present" rules — e.g.
 * EET's negative SFDR constraint where field 27 = "0" forbids Art-8/Art-9
 * fields from being filled.
 *
 * <p>Reuses {@link ConditionalRequirement} verbatim; the record's
 * {@code severityWhenMissing} is interpreted here as "severity when the
 * target field is unexpectedly present" — same field, opposite phrasing.
 */
public final class ConditionalFieldAbsenceRule implements Rule {

    private final ConditionalRequirement req;

    public ConditionalFieldAbsenceRule(ConditionalRequirement req) {
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
            if (row.stringValue(req.targetFieldNum()).isEmpty()) continue;

            String message = String.format(
                    "Field %s (%s) must be empty because field %s %s",
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
