package com.findatex.validator.template.eet;

import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.TemplateVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard against silent field-NUM renumbering in EET specs. The
 * {@code EetTemplate.EXTERNAL_VALIDATION} record references fixed NUMs
 * (3, 12, 13, 23, 24); if a future spec inserts a field that shifts the
 * sequential counter, GLEIF / OpenFIGI lookups would silently target the
 * wrong column. This test pins each NUM to its expected data-name prefix.
 */
class EetExternalValidationFieldNumStabilityTest {

    private static final Map<String, String> EXPECTED_NUM_TO_PATH_PREFIX = Map.of(
            "3",  "00030_EET_Producer_LEI",
            "12", "10010_Manufacturer_Code_Type",
            "13", "10020_Manufacturer_Code",
            "23", "20000_Financial_Instrument_Identifying_Data",
            "24", "20010_Financial_Instrument_Type_Of_Identification_Code"
    );

    static Stream<TemplateVersion> versions() {
        return new EetTemplate().versions().stream();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void externalValidationFieldNumsResolveToExpectedPaths(TemplateVersion v) {
        SpecCatalog catalog = new EetTemplate().specLoaderFor(v).load();

        for (Map.Entry<String, String> e : EXPECTED_NUM_TO_PATH_PREFIX.entrySet()) {
            FieldSpec field = catalog.byNumKey(e.getKey())
                    .orElseThrow(() -> new AssertionError(
                            "EET " + v.version() + " missing NUM=" + e.getKey()));
            assertThat(field.fundXmlPath())
                    .as("EET %s NUM=%s expected fundXmlPath to start with %s",
                            v.version(), e.getKey(), e.getValue())
                    .startsWith(e.getValue());
        }
    }
}
