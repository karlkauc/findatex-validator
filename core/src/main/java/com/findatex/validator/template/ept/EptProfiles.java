package com.findatex.validator.template.ept;

import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.template.api.TemplateId;

import java.util.List;

/**
 * EPT profiles. The set differs between versions: V2.0 publishes UCITS KIID flags (replaced
 * in V2.1 by UK FCA flags). PRIIPS sync + PRIIPs KID are stable across both versions.
 */
public final class EptProfiles {

    public static final ProfileKey PRIIPS_SYNC =
            new ProfileKey(TemplateId.EPT, "PRIIPS_SYNC", "PRIIPs Sync");

    public static final ProfileKey PRIIPS_KID =
            new ProfileKey(TemplateId.EPT, "PRIIPS_KID", "PRIIPs KID");

    public static final ProfileKey UCITS_KIID =
            new ProfileKey(TemplateId.EPT, "UCITS_KIID", "UCITS KIID");

    public static final ProfileKey UK =
            new ProfileKey(TemplateId.EPT, "UK", "UK");

    public static final ProfileSet V2_0 = new ProfileSet(TemplateId.EPT,
            List.of(PRIIPS_SYNC, PRIIPS_KID, UCITS_KIID));

    public static final ProfileSet V2_1 = new ProfileSet(TemplateId.EPT,
            List.of(PRIIPS_SYNC, PRIIPS_KID, UK));

    /** Union of profiles across all versions — used as the default {@link com.findatex.validator.template.api.TemplateDefinition#profiles()}. */
    public static final ProfileSet ALL = new ProfileSet(TemplateId.EPT,
            List.of(PRIIPS_SYNC, PRIIPS_KID, UCITS_KIID, UK));

    private EptProfiles() {
    }
}
