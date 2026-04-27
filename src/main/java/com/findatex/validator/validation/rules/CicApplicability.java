package com.findatex.validator.validation.rules;

import com.findatex.validator.domain.CicCode;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.FieldSpec;

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
        return spec.appliesToCic(cic.get().categoryDigit(), cic.get().subcategory());
    }
}
