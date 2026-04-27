package com.findatex.validator.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AppSettingsTest {

    @Test
    void defaultsAreSafe() {
        AppSettings s = AppSettings.defaults();
        assertThat(s.external().enabled()).isFalse();
        assertThat(s.external().lei().enabled()).isTrue();
        assertThat(s.external().lei().checkLapsedStatus()).isTrue();
        assertThat(s.external().lei().checkIssuerName()).isFalse();
        assertThat(s.external().lei().checkIssuerCountry()).isFalse();
        assertThat(s.external().isin().enabled()).isTrue();
        assertThat(s.external().isin().openFigiApiKey()).isEmpty();
        assertThat(s.external().isin().checkCurrency()).isFalse();
        assertThat(s.external().isin().checkCicConsistency()).isFalse();
        assertThat(s.external().cache().ttlDays()).isEqualTo(7);
        assertThat(s.proxy().mode()).isEqualTo(AppSettings.ProxyMode.SYSTEM);
    }

    @Test
    void withersReturnNewInstances() {
        AppSettings s = AppSettings.defaults();
        AppSettings s2 = s.withExternalEnabled(true);
        assertThat(s.external().enabled()).isFalse();
        assertThat(s2.external().enabled()).isTrue();
    }
}
