package com.tpt.validator.external.proxy;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SystemProxyDetectorTest {

    @Test
    void clearProxyConfigurationWipesProperties() {
        System.setProperty("http.proxyHost", "x.example");
        System.setProperty("http.proxyPort", "1234");
        SystemProxyDetector.clearProxyConfiguration();
        assertThat(System.getProperty("http.proxyHost")).isNull();
        assertThat(System.getProperty("http.proxyPort")).isNull();
    }

    @Test
    void configureProxySetsProperties() {
        SystemProxyDetector.clearProxyConfiguration();
        SystemProxyDetector.configureProxy("p.example", 8080);
        assertThat(System.getProperty("http.proxyHost")).isEqualTo("p.example");
        assertThat(System.getProperty("http.proxyPort")).isEqualTo("8080");
        SystemProxyDetector.clearProxyConfiguration();
    }

    @Test
    void getCurrentConfigEmptyByDefault() {
        SystemProxyDetector.clearProxyConfiguration();
        assertThat(SystemProxyDetector.getCurrentConfig()).isEmpty();
    }
}
