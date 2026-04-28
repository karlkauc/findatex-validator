package com.findatex.validator.template.ept;

import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EptSpecLoaderTest {

    private final EptTemplate ept = new EptTemplate();

    @Test
    void v21HasAtLeast130Fields() {
        SpecCatalog catalog = ept.specLoaderFor(EptTemplate.V2_1).load();
        assertThat(catalog.fields().size()).isGreaterThanOrEqualTo(130);
    }

    @Test
    void v20HasAtLeast110Fields() {
        SpecCatalog catalog = ept.specLoaderFor(EptTemplate.V2_0).load();
        assertThat(catalog.fields().size()).isGreaterThanOrEqualTo(110);
    }

    @Test
    void profilesPerVersionDifferOnThirdSlot() {
        // V2.0 has UCITS_KIID where V2.1 has UK; the first two are stable.
        assertThat(ept.profilesFor(EptTemplate.V2_0).all())
                .extracting(ProfileKey::code)
                .containsExactly("PRIIPS_SYNC", "PRIIPS_KID", "UCITS_KIID");

        assertThat(ept.profilesFor(EptTemplate.V2_1).all())
                .extracting(ProfileKey::code)
                .containsExactly("PRIIPS_SYNC", "PRIIPS_KID", "UK");
    }

    @Test
    void versionFieldExists() {
        assertThat(ept.specLoaderFor(EptTemplate.V2_1).load().byNumKey("1")).isPresent();
        assertThat(ept.specLoaderFor(EptTemplate.V2_0).load().byNumKey("1")).isPresent();
    }
}
