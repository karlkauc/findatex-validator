package com.findatex.validator.template.eet;

import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.template.api.TemplateId;

import java.util.List;

/**
 * Regulatory profiles supported by the European ESG Template (EET). Codes match the column
 * mapping in the {@code eet-v113-info.json} / {@code eet-v112-info.json} manifests.
 */
public final class EetProfiles {

    public static final ProfileKey SFDR_PERIODIC =
            new ProfileKey(TemplateId.EET, "SFDR_PERIODIC", "SFDR Periodic");

    public static final ProfileKey SFDR_PRECONTRACT =
            new ProfileKey(TemplateId.EET, "SFDR_PRECONTRACT", "SFDR Pre-contract");

    public static final ProfileKey SFDR_ENTITY =
            new ProfileKey(TemplateId.EET, "SFDR_ENTITY", "SFDR Entity");

    public static final ProfileKey MIFID_PRODUCTS =
            new ProfileKey(TemplateId.EET, "MIFID_PRODUCTS", "MiFID Products");

    public static final ProfileKey IDD_PRODUCTS =
            new ProfileKey(TemplateId.EET, "IDD_PRODUCTS", "IDD Products");

    public static final ProfileKey MIFID_DISTRIBUTORS =
            new ProfileKey(TemplateId.EET, "MIFID_DISTRIBUTORS", "MiFID Distributors");

    public static final ProfileKey IDD_INSURERS =
            new ProfileKey(TemplateId.EET, "IDD_INSURERS", "IDD Insurers");

    public static final ProfileKey LOOK_THROUGH =
            new ProfileKey(TemplateId.EET, "LOOK_THROUGH", "Look-through (FoF)");

    public static final ProfileSet ALL = new ProfileSet(TemplateId.EET, List.of(
            SFDR_PERIODIC, SFDR_PRECONTRACT, SFDR_ENTITY,
            MIFID_PRODUCTS, IDD_PRODUCTS,
            MIFID_DISTRIBUTORS, IDD_INSURERS,
            LOOK_THROUGH));

    private EetProfiles() {
    }
}
