package com.tpt.validator.validation.rules;

import com.tpt.validator.domain.CicCode;
import com.tpt.validator.domain.TptRow;
import com.tpt.validator.spec.FieldSpec;

import java.util.Optional;

final class CicApplicability {
    private CicApplicability() {}

    static boolean applies(FieldSpec spec, TptRow row) {
        if (spec.appliesToAllCic()) return true;
        Optional<CicCode> cic = row.cic();
        if (cic.isEmpty()) {
            // No CIC parsed -- err on the side of applicable so missing fields surface.
            return true;
        }
        return spec.appliesToCic(cic.get().categoryDigit());
    }
}
