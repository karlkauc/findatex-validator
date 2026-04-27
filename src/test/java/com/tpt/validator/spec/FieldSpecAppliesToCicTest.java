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

    /**
     * Real-spec verification for a representative sample of fields whose CIC qualifier text
     * uses the <em>unquoted</em> sub-code style ({@code x for 22}, {@code x for D4, D5}, ...).
     * Every assertion below must be derivable from the bundled TPT V7 spec — they pin the
     * extraction to its expected values so a regression in {@code parseSubcategoryQualifier}
     * shows up immediately.
     */
    @Test
    void realSpecHasCorrectWhitelistsForUnquotedQualifiers() {
        SpecCatalog c = SpecLoader.loadBundled();

        // Field 32 — Interest_rate_type:
        //   CIC7 cell: "x for 73, 74, 75" → {3,4,5}
        //   CICD cell: "x for D1, D3"     → {1,3}
        //   CICE cell: "x for E1"         → {1}
        //   CICF cell: just "x" (no sub-cat restriction)
        FieldSpec f32 = c.byNumKey("32").orElseThrow();
        assertThat(f32.applicableSubcategories().get("CIC7")).containsExactlyInAnyOrder("3", "4", "5");
        assertThat(f32.applicableSubcategories().get("CICD")).containsExactlyInAnyOrder("1", "3");
        assertThat(f32.applicableSubcategories().get("CICE")).containsExactly("1");

        // Field 33 — Coupon_rate has the same qualifiers as 32 plus CICF: "x for F1, F3, F4" → {1,3,4}
        FieldSpec f33 = c.byNumKey("33").orElseThrow();
        assertThat(f33.applicableSubcategories().get("CIC7")).containsExactlyInAnyOrder("3", "4", "5");
        assertThat(f33.applicableSubcategories().get("CICD")).containsExactlyInAnyOrder("1", "3");
        assertThat(f33.applicableSubcategories().get("CICE")).containsExactly("1");
        assertThat(f33.applicableSubcategories().get("CICF")).containsExactlyInAnyOrder("1", "3", "4");

        // Field 60 — Call_Put_Cap_Floor:  CIC2 cell: "x for 22" → {2}
        FieldSpec f60 = c.byNumKey("60").orElseThrow();
        assertThat(f60.applicableSubcategories().get("CIC2")).containsExactly("2");

        // Field 16 — Grouping_code: CICA={3}, CICB={3}, CICC={3}, CICE={2}
        FieldSpec f16 = c.byNumKey("16").orElseThrow();
        assertThat(f16.applicableSubcategories().get("CICA")).containsExactly("3");
        assertThat(f16.applicableSubcategories().get("CICB")).containsExactly("3");
        assertThat(f16.applicableSubcategories().get("CICC")).containsExactly("3");
        assertThat(f16.applicableSubcategories().get("CICE")).containsExactly("2");

        // Field 89 — Credit_quality_step_underlying: CICA={2}, CICB={2}, CICC={2}, CICF={1,2}
        FieldSpec f89 = c.byNumKey("89").orElseThrow();
        assertThat(f89.applicableSubcategories().get("CICA")).containsExactly("2");
        assertThat(f89.applicableSubcategories().get("CICB")).containsExactly("2");
        assertThat(f89.applicableSubcategories().get("CICC")).containsExactly("2");
        assertThat(f89.applicableSubcategories().get("CICF")).containsExactlyInAnyOrder("1", "2");

        // Field 90 — Modified_duration_to_maturity: CIC5={2,4}, CIC6={2,4}, CIC7={3,4,5},
        //                                           CICA={2}, CICB={2}, CICC={2}
        FieldSpec f90 = c.byNumKey("90").orElseThrow();
        assertThat(f90.applicableSubcategories().get("CIC5")).containsExactlyInAnyOrder("2", "4");
        assertThat(f90.applicableSubcategories().get("CIC6")).containsExactlyInAnyOrder("2", "4");
        assertThat(f90.applicableSubcategories().get("CIC7")).containsExactlyInAnyOrder("3", "4", "5");
        assertThat(f90.applicableSubcategories().get("CICA")).containsExactly("2");
        assertThat(f90.applicableSubcategories().get("CICB")).containsExactly("2");
        assertThat(f90.applicableSubcategories().get("CICC")).containsExactly("2");

        // Field 93 — Sensitivity_to_underlying_asset_price (delta):
        //   CIC5: "x for 51, 53, 56" → {1,3,6}
        //   CIC6: "x for 61,63, 66"  → {1,3,6}
        //   CICA: "x for A3, A5"     → {3,5}
        FieldSpec f93 = c.byNumKey("93").orElseThrow();
        assertThat(f93.applicableSubcategories().get("CIC5")).containsExactlyInAnyOrder("1", "3", "6");
        assertThat(f93.applicableSubcategories().get("CIC6")).containsExactlyInAnyOrder("1", "3", "6");
        assertThat(f93.applicableSubcategories().get("CICA")).containsExactlyInAnyOrder("3", "5");

        // End-to-end behaviour: a regular sovereign bond (CIC1 = government, e.g. "FR1?")
        // and a non-convertible corporate bond (CIC2 sub != 2,9) must NOT be flagged for
        // field 60 (Call_Put_Cap_Floor) any more.
        assertThat(f60.appliesToCic("2", "1")).isFalse();   // FR21 / DE21 / BE21 etc.
        assertThat(f60.appliesToCic("2", "9")).isFalse();   // 29 is not in {"2"}
        assertThat(f60.appliesToCic("2", "2")).isTrue();    // 22 (convertible) is allowed

        // Cross-field "if item X" patterns must not produce any sub-cat restriction so that
        // a position with an unusual sub-category isn't suppressed.
        FieldSpec f35 = c.byNumKey("35").orElseThrow();   // "x\nif item 34 is not blank" everywhere
        for (String cic : new String[]{"CIC1", "CIC2", "CIC5", "CIC6", "CIC7", "CIC8"}) {
            assertThat(f35.applicableSubcategories().get(cic))
                    .as("field 35 must have no sub-cat whitelist for %s (cross-field conditional)", cic)
                    .isNullOrEmpty();
        }
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
