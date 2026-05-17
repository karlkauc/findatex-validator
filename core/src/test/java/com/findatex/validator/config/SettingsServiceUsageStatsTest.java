package com.findatex.validator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsServiceUsageStatsTest {

    @Test
    void legacySettingsWithoutUsageBlockLoadAndGetStableInstallId(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        // A pre-usage-stats settings file: no "usageStats" key at all.
        Files.writeString(file, "{\"external\":{\"enabled\":true}}");

        SettingsService a = new SettingsService(file);
        AppSettings s = a.getCurrent();
        assertThat(s.external().enabled()).isTrue();
        assertThat(s.usageStats().enabled()).isTrue();
        String id = s.usageStats().installId();
        assertThat(id).isNotBlank();

        // The id must have been persisted, and a fresh load must keep it stable.
        assertThat(Files.readString(file)).contains(id);
        SettingsService b = new SettingsService(file);
        assertThat(b.getCurrent().usageStats().installId()).isEqualTo(id);
    }

    @Test
    void blankInstallIdIsRegeneratedAndPersisted(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        Files.writeString(file,
                "{\"usageStats\":{\"enabled\":false,\"installId\":\"\",\"endpointUrl\":\"\"}}");

        SettingsService a = new SettingsService(file);
        AppSettings s = a.getCurrent();
        assertThat(s.usageStats().enabled()).isFalse();
        String id = s.usageStats().installId();
        assertThat(id).isNotBlank();
        assertThat(Files.readString(file)).contains(id);

        SettingsService b = new SettingsService(file);
        assertThat(b.getCurrent().usageStats().installId()).isEqualTo(id);
        assertThat(b.getCurrent().usageStats().enabled()).isFalse();
    }

    @Test
    void missingFileMintsAndPersistsInstallId(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        SettingsService a = new SettingsService(file);
        String id = a.getCurrent().usageStats().installId();
        assertThat(id).isNotBlank();
        assertThat(file).exists();
        assertThat(Files.readString(file)).contains(id);
    }
}
