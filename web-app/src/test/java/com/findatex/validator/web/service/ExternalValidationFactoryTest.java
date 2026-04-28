package com.findatex.validator.web.service;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.web.dto.ExternalOptions;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(ExternalValidationFactoryTest.EnabledProfile.class)
class ExternalValidationFactoryTest {

    @Inject
    ExternalValidationFactory factory;

    @Test
    void resolveWithoutUserKeyReturnsSharedDefaultService() {
        var first = factory.resolve(Optional.empty());
        var second = factory.resolve(Optional.empty());

        assertThat(first.transientService()).isFalse();
        assertThat(second.transientService()).isFalse();
        assertThat(first.service()).isNotNull();
        assertThat(first.service()).isSameAs(second.service());
    }

    @Test
    void blankUserKeyAlsoFallsBackToDefaultService() {
        var withEmpty = factory.resolve(Optional.of(""));
        var withWhitespace = factory.resolve(Optional.of("   "));

        assertThat(withEmpty.transientService()).isFalse();
        assertThat(withWhitespace.transientService()).isFalse();
    }

    @Test
    void userKeyProducesTransientService() {
        var first = factory.resolve(Optional.of("user-supplied-key"));
        var second = factory.resolve(Optional.of("user-supplied-key"));

        assertThat(first.transientService()).isTrue();
        assertThat(second.transientService()).isTrue();
        // Each call builds a fresh instance — never cached, never shared.
        assertThat(first.service()).isNotSameAs(second.service());
    }

    @Test
    void buildSettingsPropagatesUserKeyAndFlagsWithoutPersistingThem() {
        ExternalOptions opts = new ExternalOptions(
                true,
                true, true, true, false,
                true, true, false,
                Optional.of("xyz-user-key"));

        AppSettings settings = factory.buildSettings(opts);

        assertThat(settings.external().enabled()).isTrue();
        assertThat(settings.external().lei().enabled()).isTrue();
        assertThat(settings.external().lei().checkLapsedStatus()).isTrue();
        assertThat(settings.external().lei().checkIssuerName()).isTrue();
        assertThat(settings.external().lei().checkIssuerCountry()).isFalse();
        assertThat(settings.external().isin().enabled()).isTrue();
        assertThat(settings.external().isin().checkCurrency()).isTrue();
        assertThat(settings.external().isin().checkCicConsistency()).isFalse();
        assertThat(settings.external().isin().openFigiApiKey()).isEqualTo("xyz-user-key");
    }

    @Test
    void buildSettingsFallsBackToEnvDefaultKeyWhenUserKeyAbsent() {
        ExternalOptions opts = new ExternalOptions(
                true,
                true, true, false, false,
                true, false, false,
                Optional.empty());

        AppSettings settings = factory.buildSettings(opts);

        // The test profile sets the env default below; verify the fallback wires through.
        assertThat(settings.external().isin().openFigiApiKey()).isEqualTo("env-default-key");
    }

    public static final class EnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "findatex.web.external.enabled", "true",
                    "findatex.web.external.openfigi-key", "env-default-key");
        }
    }
}
