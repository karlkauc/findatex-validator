package com.findatex.validator.template.emt;

import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.TemplateVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmtExternalValidationConfigTest {

    private final EmtTemplate emt = new EmtTemplate();

    @Test
    void configIsNonEmptyForBothVersions() {
        for (TemplateVersion v : emt.versions()) {
            ExternalValidationConfig cfg = emt.externalValidationConfigFor(v);
            assertThat(cfg.isEmpty()).as("EMT %s", v.version()).isFalse();
            assertThat(cfg.isinFields()).hasSize(1);
            // 00010/00020 type=10 (LEI), 00073 manufacturer LEI, 00003 producer LEI
            assertThat(cfg.leiFields()).hasSize(3);
        }
    }

    @Test
    void everyConfiguredFieldExistsInTheSpec() {
        for (TemplateVersion v : emt.versions()) {
            SpecCatalog catalog = emt.specLoaderFor(v).load();
            ExternalValidationConfig cfg = emt.externalValidationConfigFor(v);
            for (ExternalValidationConfig.IdentifierRef ref : cfg.isinFields()) {
                assertThat(catalog.byNumKey(ref.codeKey()))
                        .as("EMT %s ISIN codeKey %s", v.version(), ref.codeKey()).isPresent();
                if (ref.hasTypeFlag()) {
                    assertThat(catalog.byNumKey(ref.typeKey()))
                            .as("EMT %s ISIN typeKey %s", v.version(), ref.typeKey()).isPresent();
                }
            }
            for (ExternalValidationConfig.IdentifierRef ref : cfg.leiFields()) {
                assertThat(catalog.byNumKey(ref.codeKey()))
                        .as("EMT %s LEI codeKey %s", v.version(), ref.codeKey()).isPresent();
                if (ref.hasTypeFlag()) {
                    assertThat(catalog.byNumKey(ref.typeKey()))
                            .as("EMT %s LEI typeKey %s", v.version(), ref.typeKey()).isPresent();
                }
            }
        }
    }
}
