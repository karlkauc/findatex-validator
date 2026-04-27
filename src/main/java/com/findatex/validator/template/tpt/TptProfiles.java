package com.findatex.validator.template.tpt;

import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.template.api.TemplateId;

import java.util.List;

/**
 * The four regulatory profiles supported by TPT. Codes match the legacy {@code Profile} enum's
 * {@code name()} values so migration is mechanical; display names match the legacy enum's
 * {@code displayName()} return value byte-identically.
 */
public final class TptProfiles {

    public static final ProfileKey SOLVENCY_II =
            new ProfileKey(TemplateId.TPT, "SOLVENCY_II", "Solvency II");

    public static final ProfileKey IORP_EIOPA_ECB =
            new ProfileKey(TemplateId.TPT, "IORP_EIOPA_ECB", "IORP / EIOPA / ECB");

    public static final ProfileKey NW_675 =
            new ProfileKey(TemplateId.TPT, "NW_675", "NW 675");

    public static final ProfileKey SST =
            new ProfileKey(TemplateId.TPT, "SST", "SST (FINMA)");

    public static final ProfileSet ALL =
            new ProfileSet(TemplateId.TPT, List.of(SOLVENCY_II, IORP_EIOPA_ECB, NW_675, SST));

    private TptProfiles() {
    }
}
