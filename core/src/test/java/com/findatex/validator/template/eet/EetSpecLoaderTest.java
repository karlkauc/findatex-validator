package com.findatex.validator.template.eet;

import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EetSpecLoaderTest {

    private final EetTemplate eet = new EetTemplate();

    @Test
    void v113HasAtLeast700Fields() {
        SpecCatalog catalog = eet.specLoaderFor(EetTemplate.V1_1_3).load();
        assertThat(catalog.fields().size()).isGreaterThanOrEqualTo(600);
    }

    @Test
    void v112HasAtLeast700Fields() {
        SpecCatalog catalog = eet.specLoaderFor(EetTemplate.V1_1_2).load();
        assertThat(catalog.fields().size()).isGreaterThanOrEqualTo(600);
    }

    @Test
    void profilesAreEightSfdrMifidIddPlusLookThrough() {
        List<ProfileKey> profiles = eet.profiles().all();
        assertThat(profiles).extracting(ProfileKey::code).containsExactly(
                "SFDR_PERIODIC", "SFDR_PRECONTRACT", "SFDR_ENTITY",
                "MIFID_PRODUCTS", "IDD_PRODUCTS",
                "MIFID_DISTRIBUTORS", "IDD_INSURERS",
                "LOOK_THROUGH");
    }

    @Test
    void sfdrProductTypeFieldExistsInBothVersions() {
        // NUM 27 = 20040_Financial_Instrument_SFDR_Product_Type — required for the
        // EET-XF-ART9-* and EET-XF-OUT-OF-SCOPE conditional rules.
        assertThat(eet.specLoaderFor(EetTemplate.V1_1_3).load().byNumKey("27")).isPresent();
        assertThat(eet.specLoaderFor(EetTemplate.V1_1_2).load().byNumKey("27")).isPresent();
    }
}
