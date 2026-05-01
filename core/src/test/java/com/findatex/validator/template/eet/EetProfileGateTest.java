package com.findatex.validator.template.eet;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.validation.TestFileBuilder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.findatex.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the file-level "Data Reporting" Y/N flags (EET fields 6–10) suppress profiles
 * the producer has opted out of. This is the gate that eliminates ~1.5k spurious
 * SFDR_ENTITY presence findings when an asset-manager file declares
 * {@code 00080_EET_Data_Reporting_SFDR_Entity_Level = "N"}.
 */
class EetProfileGateTest {

    private static final EetTemplate EET = new EetTemplate();

    @Test
    void allYFlagsKeepEverySupportedProfile() {
        TptFile file = new TestFileBuilder()
                .row(values("6", "Y", "7", "Y", "8", "Y", "9", "Y", "10", "Y"))
                .build();
        Set<ProfileKey> requested = ordered(
                EetProfiles.SFDR_PRECONTRACT, EetProfiles.SFDR_PERIODIC,
                EetProfiles.SFDR_ENTITY, EetProfiles.MIFID_PRODUCTS,
                EetProfiles.MIFID_DISTRIBUTORS, EetProfiles.IDD_PRODUCTS,
                EetProfiles.IDD_INSURERS, EetProfiles.LOOK_THROUGH);
        Set<ProfileKey> active = EET.activeProfilesForFile(EetTemplate.V1_1_2, file, requested);
        assertThat(active).containsExactlyInAnyOrderElementsOf(requested);
    }

    @Test
    void sfdrEntityNSuppressesOnlySfdrEntity() {
        // Mirrors UBS EET (real-world): SFDR_ENTITY = N, MiFID Products = Y, others = N.
        TptFile file = new TestFileBuilder()
                .row(values("6", "N", "7", "N", "8", "N", "9", "Y", "10", "N"))
                .build();
        Set<ProfileKey> requested = ordered(
                EetProfiles.SFDR_PRECONTRACT, EetProfiles.SFDR_PERIODIC,
                EetProfiles.SFDR_ENTITY, EetProfiles.MIFID_PRODUCTS,
                EetProfiles.MIFID_DISTRIBUTORS, EetProfiles.IDD_PRODUCTS,
                EetProfiles.IDD_INSURERS, EetProfiles.LOOK_THROUGH);
        Set<ProfileKey> active = EET.activeProfilesForFile(EetTemplate.V1_1_2, file, requested);
        // Only the MiFID branch survives (Y in field 9 gates both MIFID_PRODUCTS and MIFID_DISTRIBUTORS),
        // and LOOK_THROUGH is never gated so it always passes through.
        assertThat(active).containsExactlyInAnyOrder(
                EetProfiles.MIFID_PRODUCTS,
                EetProfiles.MIFID_DISTRIBUTORS,
                EetProfiles.LOOK_THROUGH);
    }

    @Test
    void mifidFlagGovernsBothMifidProducsAndDistributors() {
        TptFile file = new TestFileBuilder()
                .row(values("9", "Y"))   // MiFID = Y, all others absent (treated as N)
                .build();
        Set<ProfileKey> active = EET.activeProfilesForFile(EetTemplate.V1_1_2, file, ordered(
                EetProfiles.MIFID_PRODUCTS, EetProfiles.MIFID_DISTRIBUTORS));
        assertThat(active).containsExactlyInAnyOrder(
                EetProfiles.MIFID_PRODUCTS, EetProfiles.MIFID_DISTRIBUTORS);
    }

    @Test
    void missingFlagsAreTreatedAsN() {
        TptFile file = new TestFileBuilder()
                .row(values("1", "V1.1.2"))   // no Reporting flags at all
                .build();
        Set<ProfileKey> active = EET.activeProfilesForFile(EetTemplate.V1_1_2, file, ordered(
                EetProfiles.SFDR_PRECONTRACT, EetProfiles.SFDR_PERIODIC, EetProfiles.SFDR_ENTITY,
                EetProfiles.LOOK_THROUGH));
        // Gated profiles all suppressed; LOOK_THROUGH (un-gated) survives.
        assertThat(active).containsExactly(EetProfiles.LOOK_THROUGH);
    }

    @Test
    void emptyRequestedSetReturnsEmpty() {
        TptFile file = new TestFileBuilder().row(values("8", "Y")).build();
        Set<ProfileKey> active = EET.activeProfilesForFile(EetTemplate.V1_1_2, file, Set.of());
        assertThat(active).isEmpty();
    }

    @Test
    void emptyFileShortCircuitsToRequestedSet() {
        // No rows at all → can't read any flag, so don't pretend the file opted out.
        TptFile file = new TestFileBuilder().build();
        Set<ProfileKey> requested = ordered(EetProfiles.SFDR_ENTITY, EetProfiles.MIFID_PRODUCTS);
        Set<ProfileKey> active = EET.activeProfilesForFile(EetTemplate.V1_1_2, file, requested);
        assertThat(active).containsExactlyInAnyOrderElementsOf(requested);
    }

    private static Set<ProfileKey> ordered(ProfileKey... profiles) {
        Set<ProfileKey> out = new LinkedHashSet<>();
        for (ProfileKey p : profiles) out.add(p);
        return out;
    }
}
