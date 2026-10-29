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
    void v8ChecksFundIssuerGroupAndCustodianLeisWithoutATypeColumn() {
        ExternalValidationConfig cfg = tpt.externalValidationConfigFor(TptTemplate.V8_0);
        assertThat(cfg.isEmpty()).isFalse();
        assertThat(cfg.isinFields()).hasSize(2);
        assertThat(cfg.leiFields()).hasSize(7);
        // 116/120/141 are "N/A, must be left empty" in V8 → 115/119/140 are single-purpose.
        assertThat(cfg.leiFields())
                .filteredOn(r -> !r.hasTypeFlag())
                .extracting(ExternalValidationConfig.IdentifierRef::codeKey)
                .containsExactlyInAnyOrder("115", "119", "140");
        assertThat(cfg.leiFields())
                .filteredOn(ExternalValidationConfig.IdentifierRef::hasTypeFlag)
                .extracting(ExternalValidationConfig.IdentifierRef::typeKey)
                .containsExactlyInAnyOrder("48", "51", "82", "85");
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
