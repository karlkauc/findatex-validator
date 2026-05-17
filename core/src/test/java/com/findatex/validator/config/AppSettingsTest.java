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

    @Test
    void newsletterDefaultsToEmptyEndpoint() {
        assertThat(AppSettings.defaults().newsletter().endpointUrl()).isEmpty();
    }

    @Test
    void legacyConstructorsLeaveNewsletterNonNull() {
        AppSettings s = new AppSettings(
                AppSettings.defaults().external(), AppSettings.defaults().proxy());
        assertThat(s.newsletter()).isNotNull();
        assertThat(s.newsletter().endpointUrl()).isEmpty();
    }

    @Test
    void withersPreserveNewsletterEndpoint() {
        AppSettings s = AppSettings.defaults()
                .withNewsletterEndpoint("https://validator.example.org");
        // The other withers must not drop the newsletter block.
        assertThat(s.withExternalEnabled(true).newsletter().endpointUrl())
                .isEqualTo("https://validator.example.org");
        assertThat(s.withFeedbackRepo("a/b").newsletter().endpointUrl())
                .isEqualTo("https://validator.example.org");
        assertThat(s.withUsageStatsEnabled(false).newsletter().endpointUrl())
                .isEqualTo("https://validator.example.org");
    }
}
