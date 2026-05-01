package com.findatex.validator.template.eet;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.template.api.ProfileKey;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the EET file-level Y/N "Data Reporting" flags (fields 6, 7, 8, 9, 10 in V1.1.2 and V1.1.3)
 * and narrows a requested {@link ProfileKey} set to those the producer has opted into.
 * Field 6 ({@code 00060_EET_Data_Reporting_SFDR_Pre_Contractual}) gates {@link EetProfiles#SFDR_PRECONTRACT};
 * field 9 ({@code 00090_EET_Data_Reporting_MiFID}) gates BOTH {@link EetProfiles#MIFID_PRODUCTS} and
 * {@link EetProfiles#MIFID_DISTRIBUTORS}, since the spec exposes only one Y/N flag for the
 * MiFID branch. {@link EetProfiles#LOOK_THROUGH} has no file-level flag and is therefore never
 * gated by this class.
 */
final class EetProfileGate {

    /** numKey → list of profiles the flag controls. Order is intentional (matches spec column order). */
    private static final Map<String, List<ProfileKey>> FLAG_TO_PROFILES;

    static {
        Map<String, List<ProfileKey>> m = new LinkedHashMap<>();
        m.put("6",  List.of(EetProfiles.SFDR_PRECONTRACT));
        m.put("7",  List.of(EetProfiles.SFDR_PERIODIC));
        m.put("8",  List.of(EetProfiles.SFDR_ENTITY));
        m.put("9",  List.of(EetProfiles.MIFID_PRODUCTS, EetProfiles.MIFID_DISTRIBUTORS));
        m.put("10", List.of(EetProfiles.IDD_PRODUCTS, EetProfiles.IDD_INSURERS));
        FLAG_TO_PROFILES = Map.copyOf(m);
    }

    private EetProfileGate() {}

    /** Profiles whose activation is governed by a file-level Y/N flag. */
    static Set<ProfileKey> gatedProfiles() {
        Set<ProfileKey> all = new LinkedHashSet<>();
        for (List<ProfileKey> profs : FLAG_TO_PROFILES.values()) all.addAll(profs);
        return all;
    }

    static Set<ProfileKey> apply(TptFile file, Set<ProfileKey> requested) {
        if (file.rows().isEmpty() || requested.isEmpty()) return requested;
        // EET file-level flags carry the same value across all rows, but we read row 1 explicitly
        // (matches the spec's "EET Data Set Information" section and avoids any per-row drift).
        TptRow first = file.rows().get(0);
        Set<ProfileKey> activated = new LinkedHashSet<>();
        for (Map.Entry<String, List<ProfileKey>> e : FLAG_TO_PROFILES.entrySet()) {
            if (isYes(first.stringValue(e.getKey()))) activated.addAll(e.getValue());
        }
        Set<ProfileKey> gated = gatedProfiles();
        Set<ProfileKey> out = new LinkedHashSet<>(requested.size());
        for (ProfileKey p : requested) {
            // If the profile is one we govern, keep it only when its flag is Y; otherwise pass it through.
            if (!gated.contains(p) || activated.contains(p)) out.add(p);
        }
        return out;
    }

    private static boolean isYes(Optional<String> v) {
        return v.map(s -> "Y".equalsIgnoreCase(s.trim())).orElse(false);
    }
}
