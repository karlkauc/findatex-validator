package com.findatex.validator.template.tpt;

import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.TemplateVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TptExternalValidationConfigTest {

    private final TptTemplate tpt = new TptTemplate();

    @Test
    void v7ExposesAllSevenLeiPairs() {
        ExternalValidationConfig cfg = tpt.externalValidationConfigFor(TptTemplate.V7_0);
        assertThat(cfg.isEmpty()).isFalse();
        assertThat(cfg.isinFields()).hasSize(2);
        assertThat(cfg.leiFields()).hasSize(7);
    }

    @Test
    void v6OmitsTheCustodianLeiPairAddedInV7() {
        ExternalValidationConfig cfg = tpt.externalValidationConfigFor(TptTemplate.V6_0);
        assertThat(cfg.isEmpty()).isFalse();
        assertThat(cfg.isinFields()).hasSize(2);
        assertThat(cfg.leiFields()).hasSize(6);
        assertThat(cfg.leiFields()).extracting(ExternalValidationConfig.IdentifierRef::codeKey)
                .doesNotContain("140");
    }

    @Test
    void everyConfiguredFieldExistsInTheSpec() {
        for (TemplateVersion v : tpt.versions()) {
            SpecCatalog catalog = tpt.specLoaderFor(v).load();
            ExternalValidationConfig cfg = tpt.externalValidationConfigFor(v);
            ConfigSanity.assertAllFieldsResolve(catalog, cfg, "TPT " + v.version());
        }
    }
}
