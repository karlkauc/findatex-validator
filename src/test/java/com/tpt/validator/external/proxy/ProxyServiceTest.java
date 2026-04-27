package com.tpt.validator.external.proxy;

import com.tpt.validator.config.AppSettings;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProxyServiceTest {

    @Test
    void noneModeClearsProperties() {
        System.setProperty("http.proxyHost", "leftover");
        ProxyConfig cfg = new ProxyConfig(AppSettings.ProxyMode.NONE, "", 0, "", "", "");
        ProxyService.applyMode(cfg);
        assertThat(System.getProperty("http.proxyHost")).isNull();
    }

    @Test
    void manualModeSetsProperties() {
        ProxyConfig cfg = new ProxyConfig(
                AppSettings.ProxyMode.MANUAL, "p.example", 8080, "u", "p", "localhost");
        ProxyService.applyMode(cfg);
        assertThat(System.getProperty("http.proxyHost")).isEqualTo("p.example");
        assertThat(System.getProperty("http.proxyPort")).isEqualTo("8080");
        assertThat(System.getProperty("http.nonProxyHosts")).isEqualTo("localhost");
        SystemProxyDetector.clearProxyConfiguration();
    }

    @Test
    void enableNtlmSetsTunnelingProperty() {
        System.clearProperty("jdk.http.auth.tunneling.disabledSchemes");
        ProxyService.enableNtlmAuthentication();
        assertThat(System.getProperty("jdk.http.auth.tunneling.disabledSchemes")).isEqualTo("");
    }
}
