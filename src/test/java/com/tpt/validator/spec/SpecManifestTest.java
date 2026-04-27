package com.tpt.validator.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SpecManifestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void tptV7ManifestParsesAndMatchesLegacySpecLoaderConstants() throws Exception {
        SpecManifest m;
        try (InputStream in = SpecManifestTest.class.getResourceAsStream("/spec/tpt/tpt-v7-info.json")) {
            assertThat(in).as("bundled tpt-v7-info.json").isNotNull();
            m = MAPPER.readValue(in, SpecManifest.class);
        }

        assertThat(m.templateId()).isEqualTo("TPT");
        assertThat(m.version()).isEqualTo("V7.0");
        assertThat(m.releaseDate()).isEqualTo("2024-11-25");
        assertThat(m.sheetName()).isEqualTo("TPT V7.0");
        assertThat(m.firstDataRow()).isEqualTo(8);

        // Columns mirror SpecLoader.java's COL_* constants.
        assertThat(m.columns().numData()).isEqualTo(1);
        assertThat(m.columns().path()).isEqualTo(2);
        assertThat(m.columns().definition()).isEqualTo(3);
        assertThat(m.columns().codification()).isEqualTo(4);
        assertThat(m.columns().comment()).isEqualTo(5);
        assertThat(m.columns().primaryFlag()).isEqualTo(11);

        // CIC applicability: 16 sub-codes CIC0..CICF in columns 12..27 (mirrors SpecLoader.CIC_COLUMNS).
        assertThat(m.applicabilityColumns().kind()).isEqualTo("CIC");
        assertThat(m.applicabilityColumns().first()).isEqualTo(12);
        assertThat(m.applicabilityColumns().last()).isEqualTo(27);
        assertThat(m.applicabilityColumns().names()).hasSize(16)
                .startsWith("CIC0").endsWith("CICF");

        // Four profile columns mirror the legacy SpecLoader profile mapping.
        assertThat(m.profileColumns()).hasSize(4);
        assertThat(m.profileColumns()).extracting(SpecManifest.ProfileColumn::code)
                .containsExactly("SOLVENCY_II", "NW_675", "SST", "IORP_EIOPA_ECB");
        SpecManifest.ProfileColumn iorp = m.profileColumns().get(3);
        assertThat(iorp.kind()).isEqualTo("presenceMerge");
        assertThat(iorp.columns()).containsExactly(31, 32, 33, 34, 35);
    }
}
