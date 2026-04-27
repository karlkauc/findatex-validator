package com.tpt.validator.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpecLoaderTest {

    @Test
    void loadsAllExpectedFields() {
        SpecCatalog c = SpecLoader.loadBundled();
        assertThat(c.fields()).hasSizeGreaterThanOrEqualTo(140);
        assertThat(c.byNumKey("12")).isPresent();
        FieldSpec f12 = c.byNumKey("12").get();
        assertThat(f12.numData()).startsWith("12_CIC_code_of_the_instrument");
        assertThat(f12.flag(Profile.SOLVENCY_II)).isEqualTo(Flag.M);
        assertThat(f12.codification().kind()).isEqualTo(CodificationKind.CIC);
    }

    @Test
    void parsesClosedListsAndCurrency() {
        SpecCatalog c = SpecLoader.loadBundled();
        FieldSpec f15 = c.byNumKey("15").orElseThrow();
        assertThat(f15.codification().kind()).isEqualTo(CodificationKind.CLOSED_LIST);
        assertThat(f15.codification().closedList())
                .extracting(CodificationDescriptor.ClosedListEntry::code)
                .contains("1", "2", "3", "99");

        FieldSpec f4 = c.byNumKey("4").orElseThrow();
        assertThat(f4.codification().kind()).isEqualTo(CodificationKind.ISO_4217);
    }

    @Test
    void cicApplicabilityParsedCorrectly() {
        SpecCatalog c = SpecLoader.loadBundled();
        FieldSpec equityOnly = c.byNumKey("13").orElseThrow();
        assertThat(equityOnly.applicableCic()).contains("CIC3");
        assertThat(equityOnly.appliesToCic("3")).isTrue();
        assertThat(equityOnly.appliesToCic("0")).isFalse();
    }

    @Test
    void sstColumnIsParsedForKnownFields() {
        // Spec column AD carries the FINMA SST flags. Sample expectations sourced
        // directly from the bundled spec — these break loudly if the column shifts.
        SpecCatalog c = SpecLoader.loadBundled();

        // Mandatory under SST: identifying portfolio + position fields.
        assertThat(c.byNumKey("1") .orElseThrow().flag(Profile.SST)).isEqualTo(Flag.M);
        assertThat(c.byNumKey("3") .orElseThrow().flag(Profile.SST)).isEqualTo(Flag.M);
        assertThat(c.byNumKey("4") .orElseThrow().flag(Profile.SST)).isEqualTo(Flag.M);
        assertThat(c.byNumKey("12").orElseThrow().flag(Profile.SST)).isEqualTo(Flag.M);

        // Optional under SST: Cash_ratio, Portfolio_modified_duration.
        assertThat(c.byNumKey("9") .orElseThrow().flag(Profile.SST)).isEqualTo(Flag.O);
        assertThat(c.byNumKey("10").orElseThrow().flag(Profile.SST)).isEqualTo(Flag.O);
    }

    @Test
    void allFourProfilesEnumerated() {
        assertThat(Profile.values()).containsExactly(
                Profile.SOLVENCY_II, Profile.IORP_EIOPA_ECB, Profile.NW_675, Profile.SST);
        assertThat(Profile.SST.displayName()).isEqualTo("SST (FINMA)");
    }
}
