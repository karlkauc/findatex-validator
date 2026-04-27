package com.tpt.validator.spec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FieldSpecAppliesToCicTest {

    /** Field 18 (Quantity) is applicable to CIC2 only for sub-categories 2 and 9. */
    @ParameterizedTest
    @CsvSource({
            "FR21, false",   // sovereign bond — same CIC class but excluded sub-category
            "BE21, false",   // user's reported case
            "DE22, true",    // convertible bond
            "DE29, true",    // other corporate bonds quoted in units
            "DE21, false",
            "DE25, false",
    })
    void corporateBondSubcategoryWhitelistFiltersWithinCic2(String cic, boolean expected) {
        FieldSpec field18 = corporateBondField();
        String cat = cic.substring(2, 3);
        String sub = cic.substring(3, 4);
        assertThat(field18.appliesToCic(cat, sub))
                .as("CIC %s should %s be applicable", cic, expected ? "" : "not")
                .isEqualTo(expected);
    }

    @Test
    void categoryOutsideListIsAlwaysFalse() {
        FieldSpec field18 = corporateBondField();
        // CIC1 (Government bonds) is not in the helper's applicability set.
        assertThat(field18.appliesToCic("1", "2")).isFalse();
    }

    @Test
    void cicWithoutSubcategoryRestrictionAcceptsEverySubcategory() {
        FieldSpec field = new FieldSpec("99_test", "Position / X", "def", "comment", "raw",
                stubCodif(),
                new EnumMap<>(Profile.class),
                Set.of("CIC2"),                  // no sub restrictions registered
                Map.of(),
                42);
        assertThat(field.appliesToCic("2", "1")).isTrue();
        assertThat(field.appliesToCic("2", "9")).isTrue();
        assertThat(field.appliesToCic("2", null)).isTrue();
    }

    @Test
    void backwardCompatibleSingleArgIgnoresSubcategoryRestriction() {
        // The old single-argument overload must remain lenient: it doesn't know the sub-category,
        // so it treats the field as applicable when the category matches and lets the row's
        // actual sub-category be inspected later.
        FieldSpec field18 = corporateBondField();
        assertThat(field18.appliesToCic("2")).isTrue();
    }

    @Test
    void specCatalogReadFromRealSpecHasSubcategoryRestrictionForField18() {
        SpecCatalog catalog = SpecLoader.loadBundled();
        FieldSpec field18 = catalog.byNumKey("18").orElseThrow();
        assertThat(field18.applicableSubcategories()).containsKey("CIC2");
        assertThat(field18.applicableSubcategories().get("CIC2"))
                .containsExactlyInAnyOrder("2", "9");
        // Future / option qualifiers should also be parsed.
        assertThat(field18.applicableSubcategories().get("CICA"))
                .containsExactlyInAnyOrder("1", "5", "9");
    }

    @Test
    void be21IsExcludedAfterRealSpecLoad() {
        // End-to-end: load the real spec and verify the user's reported case (BE21 corporate bond
        // with sub-category 1) is correctly excluded from field 18.
        SpecCatalog catalog = SpecLoader.loadBundled();
        FieldSpec field18 = catalog.byNumKey("18").orElseThrow();
        assertThat(field18.appliesToCic("2", "1")).isFalse();
        assertThat(field18.appliesToCic("2", "2")).isTrue();
        assertThat(field18.appliesToCic("2", "9")).isTrue();
    }

    private static FieldSpec corporateBondField() {
        // CIC2 has subs {"2","9"}; CIC0/CIC3/CIC4 have no sub restriction (anything goes).
        return new FieldSpec("18_Quantity", "Position / Quantity", "def", "comment", "raw",
                stubCodif(),
                new EnumMap<>(Profile.class),
                Set.of("CIC0", "CIC2", "CIC3", "CIC4"),
                Map.of("CIC2", Set.of("2", "9")),
                30);
    }

    private static CodificationDescriptor stubCodif() {
        return new CodificationDescriptor(CodificationKind.NUMERIC, Optional.empty(), List.of(), "raw");
    }
}
