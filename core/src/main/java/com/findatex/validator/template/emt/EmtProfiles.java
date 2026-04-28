package com.findatex.validator.template.emt;

import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.template.api.TemplateId;

import java.util.List;

/**
 * EMT carries no per-jurisdiction profile partition in the spec — it has a single mandatory
 * column. Modelled as one {@link ProfileKey} so the {@link com.findatex.validator.spec.FieldSpec}
 * flag map stays uniform across templates.
 */
public final class EmtProfiles {

    public static final ProfileKey EMT_BASE =
            new ProfileKey(TemplateId.EMT, "EMT_BASE", "EMT (Mandatory)");

    public static final ProfileSet ALL = new ProfileSet(TemplateId.EMT, List.of(EMT_BASE));

    private EmtProfiles() {
    }
}
