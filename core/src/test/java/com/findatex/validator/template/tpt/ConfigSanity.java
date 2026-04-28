package com.findatex.validator.template.tpt;

import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.spec.SpecCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared helper for asserting that every column referenced by an {@link ExternalValidationConfig}
 *  resolves in the loaded {@link SpecCatalog}. Lives in tpt-test package because TPT was the
 *  first template to ship the config; the helper is package-private so each per-template test
 *  re-implements its own thin wrapper if the package layout drifts. */
final class ConfigSanity {

    private ConfigSanity() {}

    static void assertAllFieldsResolve(SpecCatalog catalog, ExternalValidationConfig cfg, String label) {
        for (ExternalValidationConfig.IdentifierRef ref : cfg.isinFields()) {
            assertThat(catalog.byNumKey(ref.codeKey()))
                    .as("%s: ISIN codeKey %s", label, ref.codeKey()).isPresent();
            if (ref.hasTypeFlag()) {
                assertThat(catalog.byNumKey(ref.typeKey()))
                        .as("%s: ISIN typeKey %s", label, ref.typeKey()).isPresent();
            }
        }
        for (ExternalValidationConfig.IdentifierRef ref : cfg.leiFields()) {
            assertThat(catalog.byNumKey(ref.codeKey()))
                    .as("%s: LEI codeKey %s", label, ref.codeKey()).isPresent();
            if (ref.hasTypeFlag()) {
                assertThat(catalog.byNumKey(ref.typeKey()))
                        .as("%s: LEI typeKey %s", label, ref.typeKey()).isPresent();
            }
        }
        if (!cfg.currencyKey().isEmpty()) {
            assertThat(catalog.byNumKey(cfg.currencyKey()))
                    .as("%s: currencyKey %s", label, cfg.currencyKey()).isPresent();
        }
        if (!cfg.cicKey().isEmpty()) {
            assertThat(catalog.byNumKey(cfg.cicKey()))
                    .as("%s: cicKey %s", label, cfg.cicKey()).isPresent();
        }
        if (!cfg.issuerNameKey().isEmpty()) {
            assertThat(catalog.byNumKey(cfg.issuerNameKey()))
                    .as("%s: issuerNameKey %s", label, cfg.issuerNameKey()).isPresent();
        }
        if (!cfg.issuerCountryKey().isEmpty()) {
            assertThat(catalog.byNumKey(cfg.issuerCountryKey()))
                    .as("%s: issuerCountryKey %s", label, cfg.issuerCountryKey()).isPresent();
        }
    }
}
