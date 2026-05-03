package com.findatex.validator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OpenFIGI key sits next to the proxy password in {@code settings.json}.
 * Pre-encryption builds wrote it as plaintext; the current build encrypts it
 * via {@link PasswordCipher} on save and decrypts on load. The record always
 * holds plaintext in memory so existing callers
 * ({@code ExternalValidationService}, the JavaFX Settings dialog,
 * {@code ExternalValidationFactory}) need no awareness of the change.
 */
class OpenFigiKeyEncryptionTest {

    @Test
    void roundTripEncryptsOnDiskButReturnsPlaintext(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        SettingsService svc = new SettingsService(file);

        AppSettings withKey = withOpenFigiKey(svc.getCurrent(), "secret-test-key-XYZ");
        svc.update(withKey);

        // On disk the key must NOT appear verbatim.
        String onDisk = Files.readString(file);
        assertThat(onDisk).doesNotContain("secret-test-key-XYZ");
        assertThat(onDisk).contains("openFigiApiKey");

        // After reload the in-memory record exposes the original plaintext.
        SettingsService reloaded = new SettingsService(file);
        assertThat(reloaded.getCurrent().external().isin().openFigiApiKey())
                .isEqualTo("secret-test-key-XYZ");
    }

    @Test
    void legacyPlaintextIsTransparentlyMigratedOnFirstRead(@TempDir Path tmp) throws Exception {
        // Simulate a settings.json from a pre-encryption build: the OpenFIGI
        // key is stored verbatim. The constructor must surface it as plaintext
        // (so the user doesn't lose their key on upgrade) and rewrite the file
        // in encrypted form so the next disk inspection no longer sees it.
        Path file = tmp.resolve("settings.json");
        Files.writeString(file, "{\n"
                + "  \"external\" : {\n"
                + "    \"enabled\" : false,\n"
                + "    \"isin\" : { \"enabled\" : true, \"openFigiApiKey\" : \"legacy-plain-XYZ\" }\n"
                + "  }\n"
                + "}");

        SettingsService svc = new SettingsService(file);

        // In-memory: plaintext preserved.
        assertThat(svc.getCurrent().external().isin().openFigiApiKey())
                .isEqualTo("legacy-plain-XYZ");
        // On-disk: the constructor's auto-rewrite has replaced the plaintext
        // with the AES-GCM ciphertext (Base64). The literal must be gone.
        String onDisk = Files.readString(file);
        assertThat(onDisk).doesNotContain("legacy-plain-XYZ");
    }

    @Test
    void emptyKeyStaysEmpty(@TempDir Path tmp) throws Exception {
        // PasswordCipher.encrypt("") returns "", so the round-trip for an
        // unset key must not introduce a non-empty ciphertext blob.
        Path file = tmp.resolve("settings.json");
        SettingsService svc = new SettingsService(file);
        svc.update(svc.getCurrent()); // defaults — empty key
        assertThat(svc.getCurrent().external().isin().openFigiApiKey()).isEmpty();

        SettingsService reloaded = new SettingsService(file);
        assertThat(reloaded.getCurrent().external().isin().openFigiApiKey()).isEmpty();
    }

    private static AppSettings withOpenFigiKey(AppSettings s, String key) {
        AppSettings.External e = s.external();
        AppSettings.Isin isin = e.isin();
        AppSettings.Isin newIsin = new AppSettings.Isin(
                isin.enabled(), key, isin.checkCurrency(), isin.checkCicConsistency());
        return new AppSettings(
                new AppSettings.External(e.enabled(), e.lei(), newIsin, e.cache()),
                s.proxy());
    }
}
