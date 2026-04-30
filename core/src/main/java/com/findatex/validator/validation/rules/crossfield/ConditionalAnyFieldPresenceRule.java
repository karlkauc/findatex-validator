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
 * Generic cross-field rule that emits one finding per row whenever the
 * source predicate holds AND every target field listed in
 * {@link ConditionalAnyOfRequirement#targetFieldNums()} is blank.
 *
 * <p>The single-finding-per-row shape (instead of one-per-target) is
 * intentional: the rule expresses a single attribution requirement, not
 * a per-field mandate. The finding's {@code fieldNum} points at the
 * first target NUM in the requirement so that {@code FindingEnricher} and
 * the report's "Per Position" sheet have a stable anchor.
 */
public final class ConditionalAnyFieldPresenceRule implements Rule {

    private final ConditionalAnyOfRequirement req;

    public ConditionalAnyFieldPresenceRule(ConditionalAnyOfRequirement req) {
        this.req = req;
    }

    @Override
    public String id() { return req.ruleId(); }

    public ConditionalAnyOfRequirement requirement() { return req; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        String anchorNum = req.targetFieldNums().get(0);
        String anchorName = ctx.catalog().byNumKey(anchorNum)
                .map(FieldSpec::numData)
                .orElse("Field " + anchorNum);

        for (TptRow row : ctx.file().rows()) {
            String sourceVal = row.stringValue(req.sourceFieldNum()).orElse(null);
            if (!req.condition().test(sourceVal)) continue;

            boolean anyPresent = req.targetFieldNums().stream()
                    .anyMatch(num -> row.stringValue(num).isPresent());
            if (anyPresent) continue;

            String message = String.format(
                    "At least one of fields %s must be present because field %s %s",
                    req.targetFieldNums(),
                    req.sourceFieldNum(),
                    req.condition().describe());
            out.add(emit(req.ruleId(), req.severityWhenAllMissing(),
                    anchorNum, anchorName, row.rowIndex(), message));
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
