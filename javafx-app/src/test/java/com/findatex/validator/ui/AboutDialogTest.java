package com.findatex.validator.ui;

import com.findatex.validator.AppInfo;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AboutDialogTest {

    @Test
    void bundledAboutUsesVersionPlaceholderAndItIsSubstituted() throws Exception {
        String raw;
        try (InputStream in = AboutDialog.class.getResourceAsStream("/about/ABOUT.md")) {
            assertThat(in).isNotNull();
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(raw).contains("**Version {{version}}**").doesNotContain("Version 1.0.0");

        String rendered = AboutDialog.substituteVersion(raw);
        assertThat(rendered)
                .contains("**Version " + AppInfo.version() + "**")
                .doesNotContain("{{version}}");
    }
}
