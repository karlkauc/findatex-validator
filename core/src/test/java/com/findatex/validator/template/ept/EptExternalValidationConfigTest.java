package com.findatex.validator.template.ept;

import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.TemplateVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EptExternalValidationConfigTest {

    private final EptTemplate ept = new EptTemplate();

    @Test
    void configIsNonEmptyForBothVersions() {
        for (TemplateVersion v : ept.versions()) {
            ExternalValidationConfig cfg = ept.externalValidationConfigFor(v);
            assertThat(cfg.isEmpty()).as("EPT %s", v.version()).isFalse();
            assertThat(cfg.isinFields()).hasSize(1);
            // 00030/00040 type=9 (LEI) plus 00016 manufacturer LEI
            assertThat(cfg.leiFields()).hasSize(2);
        }
    }

    @Test
    void everyConfiguredFieldExistsInTheSpec() {
        for (TemplateVersion v : ept.versions()) {
            SpecCatalog catalog = ept.specLoaderFor(v).load();
            ExternalValidationConfig cfg = ept.externalValidationConfigFor(v);
            for (ExternalValidationConfig.IdentifierRef ref : cfg.isinFields()) {
                assertThat(catalog.byNumKey(ref.codeKey()))
                        .as("EPT %s ISIN codeKey %s", v.version(), ref.codeKey()).isPresent();
                if (ref.hasTypeFlag()) {
                    assertThat(catalog.byNumKey(ref.typeKey()))
                            .as("EPT %s ISIN typeKey %s", v.version(), ref.typeKey()).isPresent();
                }
            }
            for (ExternalValidationConfig.IdentifierRef ref : cfg.leiFields()) {
                assertThat(catalog.byNumKey(ref.codeKey()))
                        .as("EPT %s LEI codeKey %s", v.version(), ref.codeKey()).isPresent();
                if (ref.hasTypeFlag()) {
                    assertThat(catalog.byNumKey(ref.typeKey()))
                            .as("EPT %s LEI typeKey %s", v.version(), ref.typeKey()).isPresent();
                }
            }
        }
    }
}
