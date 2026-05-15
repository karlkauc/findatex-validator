package com.findatex.validator.validation;

import com.findatex.validator.spec.CodificationDescriptor;
import com.findatex.validator.spec.CodificationKind;
import com.findatex.validator.spec.EmptyApplicabilityScope;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.Flag;
import com.findatex.validator.spec.SpecCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FieldNameFormatterTest {

    private static final CodificationDescriptor FREE_TEXT_CODIF = new CodificationDescriptor(
            CodificationKind.FREE_TEXT, Optional.empty(), List.of(), "");

    private static SpecCatalog catalogWith(String numData, String name) {
        FieldSpec f = new FieldSpec(numData, name, null, null, null, null,
                FREE_TEXT_CODIF, Map.<String, Flag>of(),
                EmptyApplicabilityScope.INSTANCE, 0);
        return new SpecCatalog(List.of(f));
    }

    @Test
    void usesCatalogNameForTptShapedNumData() {
        // For TPT, FieldSpec.name() defaults to numData (e.g. "33_Coupon_rate").
        SpecCatalog cat = catalogWith("33_Coupon_rate", null);
        assertThat(FieldNameFormatter.format("33", "Field 33", cat))
                .isEqualTo("Field 33 (Coupon rate)");
    }

    @Test
    void rewritesBareFieldNFromInconsistentRule() {
        // InterestRateTypeRule emits a bare "Field 34" — the formatter should fill in the description.
        SpecCatalog cat = catalogWith("34_Reference_index", null);
        assertThat(FieldNameFormatter.format("34", "Field 34", cat))
                .isEqualTo("Field 34 (Reference index)");
    }

    @Test
    void leavesHardcodedDescriptionStableWhenCatalogAgrees() {
        SpecCatalog cat = catalogWith("9_Cash_percentage", null);
        assertThat(FieldNameFormatter.format("9", "Field 9 (Cash percentage)", cat))
                .isEqualTo("Field 9 (Cash percentage)");
    }

    @Test
    void usesExplicitNameColumnForManifestDrivenTemplates() {
        // EET/EMT/EPT: FieldSpec.name() comes from column B (e.g. "00010_EET_Version").
        SpecCatalog cat = catalogWith("00010", "00010_EET_Version");
        assertThat(FieldNameFormatter.format("00010", "Field 00010", cat))
                .isEqualTo("Field 00010 (EET Version)");
    }

    @Test
    void fallsBackToBareFieldWhenCatalogMissingAndRawHasNoDescription() {
        SpecCatalog empty = new SpecCatalog(List.of());
        assertThat(FieldNameFormatter.format("999", "Field 999", empty))
                .isEqualTo("Field 999");
    }

    @Test
    void recoversDescriptionFromRawWhenCatalogMissing() {
        SpecCatalog empty = new SpecCatalog(List.of());
        assertThat(FieldNameFormatter.format("141", "Field 141 (Type of custodian identification code)", empty))
                .isEqualTo("Field 141 (Type of custodian identification code)");
    }

    @Test
    void recoversDescriptionFromUnderscoredRawWhenCatalogMissing() {
        SpecCatalog empty = new SpecCatalog(List.of());
        assertThat(FieldNameFormatter.format("33", "33_Coupon_rate", empty))
                .isEqualTo("Field 33 (Coupon rate)");
    }

    @Test
    void leavesPortfolioOrGlobalFindingsUntouched() {
        // Cross-portfolio / external-service findings carry no fieldNum — the formatter
        // must not invent a "Field null".
        SpecCatalog cat = catalogWith("1", "1_anything");
        assertThat(FieldNameFormatter.format(null, "External validation", cat))
                .isEqualTo("External validation");
        assertThat(FieldNameFormatter.format("", "External validation", cat))
                .isEqualTo("External validation");
    }
}
