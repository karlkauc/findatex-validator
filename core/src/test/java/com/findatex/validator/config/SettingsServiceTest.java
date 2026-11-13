package com.findatex.validator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsServiceTest {

    @Test
    void missingFileYieldsDefaults(@TempDir Path tmp) {
        SettingsService svc = new SettingsService(tmp.resolve("settings.json"));
        AppSettings s = svc.getCurrent();
        assertThat(s.external().enabled()).isFalse();
        assertThat(s.proxy().mode()).isEqualTo(AppSettings.ProxyMode.SYSTEM);
    }

    @Test
    void roundTrip(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        SettingsService a = new SettingsService(file);
        a.update(a.getCurrent().withExternalEnabled(true));
        assertThat(Files.readString(file)).contains("\"enabled\" : true");

        SettingsService b = new SettingsService(file);
        assertThat(b.getCurrent().external().enabled()).isTrue();
    }

    @Test
    void atomicWriteCreatesNoTempFiles(@TempDir Path tmp) {
        Path file = tmp.resolve("settings.json");
        SettingsService svc = new SettingsService(file);
        svc.update(svc.getCurrent());
        assertThat(tmp.toFile().listFiles()).hasSize(1);
        assertThat(file).exists();
    }

    @Test
    void unknownFieldsAreIgnored(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        Files.writeString(file, "{\"external\":{\"enabled\":true,\"unknown\":42}}");
        SettingsService svc = new SettingsService(file);
        assertThat(svc.getCurrent().external().enabled()).isTrue();
    }

    @Test
    void partialFileGetsDefaultsForMissingBlocks(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        // Hand-edited file with only the usage-stats block: no external, no proxy.
        Files.writeString(file, "{\"usageStats\":{\"enabled\":true,\"installId\":\"\","
                + "\"endpointUrl\":\"http://127.0.0.1:1/api/usage-stats\"},"
                + "\"external\":{\"enabled\":true}}");
        AppSettings s = new SettingsService(file).getCurrent();
        AppSettings d = AppSettings.defaults();
        assertThat(s.proxy()).isEqualTo(d.proxy());
        assertThat(s.external().enabled()).isTrue();
        assertThat(s.external().lei()).isEqualTo(d.external().lei());
        assertThat(s.external().isin()).isEqualTo(d.external().isin());
        assertThat(s.external().cache()).isEqualTo(d.external().cache());
        assertThat(s.usageStats().endpointUrl()).isEqualTo("http://127.0.0.1:1/api/usage-stats");
    }

    @Test
    void legacyFileWithoutFeedbackBlockLoadsWithEmptyRepo(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        // A settings.json written by a pre-feedback release: no "feedback" key.
        Files.writeString(file, "{\"external\":{\"enabled\":false},"
                + "\"proxy\":{\"mode\":\"SYSTEM\"}}");
        SettingsService svc = new SettingsService(file);
        assertThat(svc.getCurrent().feedback()).isNotNull();
        assertThat(svc.getCurrent().feedback().githubRepo()).isEmpty();
    }

    @Test
    void feedbackRepoSurvivesRoundTrip(@TempDir Path tmp) {
        Path file = tmp.resolve("settings.json");
        SettingsService a = new SettingsService(file);
        a.update(a.getCurrent().withFeedbackRepo("karlkauc/findatex-validator"));
        SettingsService b = new SettingsService(file);
        assertThat(b.getCurrent().feedback().githubRepo())
                .isEqualTo("karlkauc/findatex-validator");
    }

    @Test
    void updateThrowsOnUnwritableTarget(@TempDir Path tmp) throws Exception {
        // Use a path whose parent directory we just created and made read-only.
        Path readOnlyDir = Files.createDirectory(tmp.resolve("readonly"));
        Path file = readOnlyDir.resolve("settings.json");
        try {
            readOnlyDir.toFile().setWritable(false);
            SettingsService svc = new SettingsService(file);
            assertThatThrownBy(() -> svc.update(svc.getCurrent().withExternalEnabled(true)))
                    .isInstanceOf(java.io.UncheckedIOException.class);
        } finally {
            readOnlyDir.toFile().setWritable(true);
        }
    }
}
