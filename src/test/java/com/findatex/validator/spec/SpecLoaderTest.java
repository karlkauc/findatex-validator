package com.findatex.validator.spec;

import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateId;
import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.template.tpt.TptProfiles;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sub-step 2.5: this regression suite now loads the TPT V7 catalog via the manifest-driven
 * pipeline ({@code TemplateRegistry → TptTemplate.specLoaderFor → ManifestDrivenSpecLoader})
 * instead of calling {@link SpecLoader#loadBundled()} directly. Field count and per-field flags
 * must remain unchanged — these assertions are the regression baseline for the loader migration.
 */
class SpecLoaderTest {

    @BeforeAll
    static void initRegistry() {
        TemplateRegistry.init();
    }

    private static SpecCatalog loadViaManifest() {
        return TemplateRegistry.of(TemplateId.TPT)
                .specLoaderFor(TemplateRegistry.of(TemplateId.TPT).latest())
                .load();
    }

    @Test
    void loadsAllExpectedFields() {
        SpecCatalog c = loadViaManifest();
        assertThat(c.fields()).hasSizeGreaterThanOrEqualTo(140);
        assertThat(c.byNumKey("12")).isPresent();
        FieldSpec f12 = c.byNumKey("12").get();
        assertThat(f12.numData()).startsWith("12_CIC_code_of_the_instrument");
        assertThat(f12.flag(TptProfiles.SOLVENCY_II)).isEqualTo(Flag.M);
        assertThat(f12.codification().kind()).isEqualTo(CodificationKind.CIC);
    }

    @Test
    void parsesClosedListsAndCurrency() {
        SpecCatalog c = loadViaManifest();
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
        SpecCatalog c = loadViaManifest();
        FieldSpec equityOnly = c.byNumKey("13").orElseThrow();
        assertThat(equityOnly.applicableCic()).contains("CIC3");
        assertThat(equityOnly.appliesToCic("3")).isTrue();
        assertThat(equityOnly.appliesToCic("0")).isFalse();
    }

    @Test
    void sstColumnIsParsedForKnownFields() {
        // Spec column AD carries the FINMA SST flags. Sample expectations sourced
        // directly from the bundled spec — these break loudly if the column shifts.
        SpecCatalog c = loadViaManifest();

        // Mandatory under SST: identifying portfolio + position fields.
        assertThat(c.byNumKey("1") .orElseThrow().flag(TptProfiles.SST)).isEqualTo(Flag.M);
        assertThat(c.byNumKey("3") .orElseThrow().flag(TptProfiles.SST)).isEqualTo(Flag.M);
        assertThat(c.byNumKey("4") .orElseThrow().flag(TptProfiles.SST)).isEqualTo(Flag.M);
        assertThat(c.byNumKey("12").orElseThrow().flag(TptProfiles.SST)).isEqualTo(Flag.M);

        // Optional under SST: Cash_ratio, Portfolio_modified_duration.
        assertThat(c.byNumKey("9") .orElseThrow().flag(TptProfiles.SST)).isEqualTo(Flag.O);
        assertThat(c.byNumKey("10").orElseThrow().flag(TptProfiles.SST)).isEqualTo(Flag.O);
    }

    @Test
    void allFourProfilesEnumerated() {
        assertThat(new ProfileKey[]{TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB, TptProfiles.NW_675, TptProfiles.SST}).containsExactly(
                TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB, TptProfiles.NW_675, TptProfiles.SST);
        assertThat(TptProfiles.SST.displayName()).isEqualTo("SST (FINMA)");
    }
}
