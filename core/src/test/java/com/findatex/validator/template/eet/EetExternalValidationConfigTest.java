package com.findatex.validator.template.eet;

import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.TemplateVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EetExternalValidationConfigTest {

    private final EetTemplate eet = new EetTemplate();

    @Test
    void configIsNonEmptyForBothVersions() {
        for (TemplateVersion v : eet.versions()) {
            ExternalValidationConfig cfg = eet.externalValidationConfigFor(v);
            assertThat(cfg.isEmpty()).as("EET %s", v.version()).isFalse();
            // 1 ISIN ref (20000/20010 type=1) + 3 LEI refs (20000/20010 type=10, 10020/10010 L, 00030 alone)
            assertThat(cfg.isinFields()).hasSize(1);
            assertThat(cfg.leiFields()).hasSize(3);
        }
    }

    @Test
    void everyConfiguredFieldExistsInTheSpec() {
        for (TemplateVersion v : eet.versions()) {
            SpecCatalog catalog = eet.specLoaderFor(v).load();
            ExternalValidationConfig cfg = eet.externalValidationConfigFor(v);
            for (ExternalValidationConfig.IdentifierRef ref : cfg.isinFields()) {
                assertThat(catalog.byNumKey(ref.codeKey()))
                        .as("EET %s ISIN codeKey %s", v.version(), ref.codeKey()).isPresent();
                if (ref.hasTypeFlag()) {
                    assertThat(catalog.byNumKey(ref.typeKey()))
                            .as("EET %s ISIN typeKey %s", v.version(), ref.typeKey()).isPresent();
                }
            }
            for (ExternalValidationConfig.IdentifierRef ref : cfg.leiFields()) {
                assertThat(catalog.byNumKey(ref.codeKey()))
                        .as("EET %s LEI codeKey %s", v.version(), ref.codeKey()).isPresent();
                if (ref.hasTypeFlag()) {
                    assertThat(catalog.byNumKey(ref.typeKey()))
                            .as("EET %s LEI typeKey %s", v.version(), ref.typeKey()).isPresent();
                }
            }
        }
    }
}
