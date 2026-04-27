package com.tpt.validator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

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
}
