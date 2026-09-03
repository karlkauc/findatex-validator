package com.findatex.validator;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AppInfoTest {

    @Test
    void usageTokenIsNeverAnUnresolvedMavenPlaceholder() {
        // Default build (no FINDATEX_USAGE_TOKEN in the build env) must yield ""
        // rather than the literal "${findatex.usage.token}".
        String token = AppInfo.usageToken();
        assertThat(token).isNotNull();
        assertThat(token).doesNotStartWith("${");
    }

    @Test
    void buildPropertiesCarryTheUsageTokenKey() throws Exception {
        Properties p = new Properties();
        try (InputStream in = AppInfo.class.getClassLoader()
                .getResourceAsStream("META-INF/findatex-validator.properties")) {
            assertThat(in).isNotNull();
            p.load(in);
        }
        // The key must be present (filtered at build time); its value may be empty.
        assertThat(p.containsKey("usageToken")).isTrue();
        assertThat(p.getProperty("usageToken")).doesNotContain("${");
    }
}
