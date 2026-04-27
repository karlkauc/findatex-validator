package com.findatex.validator.spec;

import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FlagAndFieldSpecTest {

    @ParameterizedTest
    @CsvSource({
            "M,    M",
            "C,    C",
            "O,    O",
            "I,    I",
            "N/A,  NA",
            "NA,   NA",
            "m,    M",          // case-insensitive
            "  C , C",          // trimmed
            "?,    UNKNOWN",
            "X,    UNKNOWN",
    })
    void flagParseHandlesAllVariants(String raw, String expected) {
        assertThat(Flag.parse(raw)).isEqualTo(Flag.valueOf(expected));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankFlagBecomesUnknown(String raw) {
        assertThat(Flag.parse(raw)).isEqualTo(Flag.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({
            "12_CIC_code,    12",
            "8b_Total,       8b",
            "105a_Foo,       105a",
            "105b_Bar,       105b",
            "1000_Version,   1000",
            "  12_padded ,   12",
            "1000,            1000",     // no underscore
    })
    void extractNumKeyHandlesAllNumDataShapes(String numData, String expected) {
        assertThat(FieldSpec.extractNumKey(numData)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void extractNumKeyHandlesNullAndEmpty(String numData) {
        assertThat(FieldSpec.extractNumKey(numData)).isEmpty();
    }

    @Test
    void flagAccessorsReturnDefaultUnknownForMissingProfile() {
        FieldSpec spec = makeFieldSpec(Map.of(TptProfiles.SOLVENCY_II, Flag.M));
        assertThat(spec.flag(TptProfiles.SOLVENCY_II)).isEqualTo(Flag.M);
        assertThat(spec.flag(TptProfiles.NW_675)).isEqualTo(Flag.UNKNOWN);
    }

    @Test
    void mandatoryAndConditionalFlagShortcutsWork() {
        FieldSpec mandatory = makeFieldSpec(Map.of(TptProfiles.SOLVENCY_II, Flag.M));
        FieldSpec conditional = makeFieldSpec(Map.of(TptProfiles.SOLVENCY_II, Flag.C));
        FieldSpec optional = makeFieldSpec(Map.of(TptProfiles.SOLVENCY_II, Flag.O));

        assertThat(mandatory.isMandatoryFor(TptProfiles.SOLVENCY_II)).isTrue();
        assertThat(mandatory.isConditionalFor(TptProfiles.SOLVENCY_II)).isFalse();

        assertThat(conditional.isMandatoryFor(TptProfiles.SOLVENCY_II)).isFalse();
        assertThat(conditional.isConditionalFor(TptProfiles.SOLVENCY_II)).isTrue();

        assertThat(optional.isMandatoryFor(TptProfiles.SOLVENCY_II)).isFalse();
        assertThat(optional.isConditionalFor(TptProfiles.SOLVENCY_II)).isFalse();
    }

    @Test
    void appliesToAllCicWhenSetIsEmpty() {
        FieldSpec spec = makeFieldSpec(Map.of(), Set.of());
        assertThat(spec.appliesToAllCic()).isTrue();
        assertThat(spec.appliesToCic("0")).isTrue();
        assertThat(spec.appliesToCic("F")).isTrue();
        assertThat(spec.appliesToCic(null)).isTrue();
    }

    @Test
    void appliesToAllCicWhenAll16Listed() {
        Set<String> all = Set.of("CIC0","CIC1","CIC2","CIC3","CIC4","CIC5","CIC6","CIC7",
                                 "CIC8","CIC9","CICA","CICB","CICC","CICD","CICE","CICF");
        FieldSpec spec = makeFieldSpec(Map.of(), all);
        assertThat(spec.appliesToAllCic()).isTrue();
    }

    @Test
    void appliesToCicMatchesByCategoryDigit() {
        FieldSpec equityOnly = makeFieldSpec(Map.of(), Set.of("CIC3"));
        assertThat(equityOnly.appliesToCic("3")).isTrue();
        assertThat(equityOnly.appliesToCic("a")).isFalse();
        assertThat(equityOnly.appliesToCic("A")).isFalse();
    }

    @Test
    void appliesToCicWithNullDigitDelegatesToAllCheck() {
        // null digit → falls back to appliesToAllCic(): false when only specific CICs are listed,
        // true when the field applies to all CICs.
        FieldSpec equityOnly = makeFieldSpec(Map.of(), Set.of("CIC3"));
        assertThat(equityOnly.appliesToCic(null)).isFalse();

        FieldSpec everywhere = makeFieldSpec(Map.of(), Set.of());
        assertThat(everywhere.appliesToCic(null)).isTrue();
    }

    @Test
    void appliesToCicLowercaseDigitNormalised() {
        FieldSpec futuresOnly = makeFieldSpec(Map.of(), Set.of("CICA"));
        assertThat(futuresOnly.appliesToCic("a")).isTrue();
    }

    private static FieldSpec makeFieldSpec(Map<ProfileKey, Flag> flags) {
        return makeFieldSpec(flags, Set.of());
    }

    private static FieldSpec makeFieldSpec(Map<ProfileKey, Flag> flags, Set<String> cics) {
        Map<ProfileKey, Flag> map = new java.util.HashMap<ProfileKey, Flag>();
        map.putAll(flags);
        return new FieldSpec("12_test", "Position / Test", "def", "comment",
                "raw", new CodificationDescriptor(CodificationKind.UNKNOWN, java.util.Optional.empty(),
                        List.of(), "raw"),
                map, cics, 99);
    }
}
