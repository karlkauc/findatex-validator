package com.findatex.validator.template.emt;

import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmtSpecLoaderTest {

    private final EmtTemplate emt = new EmtTemplate();

    @Test
    void v43HasAtLeast100Fields() {
        SpecCatalog catalog = emt.specLoaderFor(EmtTemplate.V4_3).load();
        assertThat(catalog.fields().size()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void v42HasAtLeast100Fields() {
        SpecCatalog catalog = emt.specLoaderFor(EmtTemplate.V4_2).load();
        assertThat(catalog.fields().size()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void singleProfileEmtBase() {
        assertThat(emt.profiles().all())
                .extracting(ProfileKey::code)
                .containsExactly("EMT_BASE");
    }

    @Test
    void versionFieldExists() {
        assertThat(emt.specLoaderFor(EmtTemplate.V4_3).load().byNumKey("1")).isPresent();
        assertThat(emt.specLoaderFor(EmtTemplate.V4_2).load().byNumKey("1")).isPresent();
    }
}
