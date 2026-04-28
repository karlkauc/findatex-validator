package com.findatex.validator.external;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.template.eet.EetTemplate;
import com.findatex.validator.template.emt.EmtTemplate;
import com.findatex.validator.template.ept.EptTemplate;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.TestFileBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.findatex.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link ExternalValidationService} respects the per-template
 * {@link ExternalValidationConfig} when run against EET/EMT/EPT-shaped rows. The TPT path is
 * already covered by {@link ExternalValidationServiceTest}.
 */
class ExternalValidationServiceMultiTemplateTest {

    private static final String VALID_LEI = "529900D6BF99LW9R2E68";
    private static final String VALID_ISIN = "US0378331005";

    @Test
    void eetIsinHitOnPolymorphicIdentifierWithType1(@TempDir Path tmp) {
        // EET: field 23 holds the identifier, 24 the type-of-code flag, "1" ⇒ ISIN
        TptFile file = new TestFileBuilder()
                .row(values("23", VALID_ISIN, "24", "1"))
                .build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp, leis -> Map.of(), isins -> Map.of());

        List<Finding> out = svc.run(file, EetTemplate.EXTERNAL_VALIDATION, settings, () -> false);

        assertThat(out).extracting(Finding::ruleId).contains("ISIN-LIVE/23/24");
        assertThat(out).extracting(Finding::severity).contains(Severity.ERROR);
    }

    @Test
    void eetLeiHitOnPolymorphicIdentifierWithType10(@TempDir Path tmp) {
        // Same column 23, but type "10" ⇒ candidate LEI
        TptFile file = new TestFileBuilder()
                .row(values("23", VALID_LEI, "24", "10"))
                .build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp, leis -> Map.of(), isins -> Map.of());

        List<Finding> out = svc.run(file, EetTemplate.EXTERNAL_VALIDATION, settings, () -> false);

        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE/23/24");
    }

    @Test
    void eetLeiHitOnDedicatedProducerColumnWithoutTypeFlag(@TempDir Path tmp) {
        // EET field 3 = EET_Producer_LEI, alphanum, no type flag — every syntactically valid LEI is a candidate
        TptFile file = new TestFileBuilder()
                .row(values("3", VALID_LEI))
                .build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp, leis -> Map.of(), isins -> Map.of());

        List<Finding> out = svc.run(file, EetTemplate.EXTERNAL_VALIDATION, settings, () -> false);

        // typeKey is empty — rule ID has a trailing slash by convention.
        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE/3/");
    }

    @Test
    void eetManufacturerLeiOnlyCollectedWhenTypeFlagIsL(@TempDir Path tmp) {
        // Field 12 = "L" ⇒ field 13 (manufacturer code) is a LEI; "N" ⇒ ignore
        TptFile rowWithLei = new TestFileBuilder()
                .row(values("13", VALID_LEI, "12", "L")).build();
        TptFile rowWithoutLei = new TestFileBuilder()
                .row(values("13", VALID_LEI, "12", "N")).build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp, leis -> Map.of(), isins -> Map.of());

        List<Finding> withL = svc.run(rowWithLei, EetTemplate.EXTERNAL_VALIDATION, settings, () -> false);
        List<Finding> withN = svc.run(rowWithoutLei, EetTemplate.EXTERNAL_VALIDATION, settings, () -> false);

        assertThat(withL).extracting(Finding::ruleId).contains("LEI-LIVE/13/12");
        assertThat(withN).extracting(Finding::ruleId).doesNotContain("LEI-LIVE/13/12");
    }

    @Test
    void emtIsinHitWithType1(@TempDir Path tmp) {
        // EMT: field 9 identifier, 10 type flag
        TptFile file = new TestFileBuilder()
                .row(values("9", VALID_ISIN, "10", "1"))
                .build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp, leis -> Map.of(), isins -> Map.of());

        List<Finding> out = svc.run(file, EmtTemplate.EXTERNAL_VALIDATION, settings, () -> false);
        assertThat(out).extracting(Finding::ruleId).contains("ISIN-LIVE/9/10");
    }

    @Test
    void emtManufacturerLeiOnDedicatedColumn(@TempDir Path tmp) {
        // EMT field 20 = manufacturer LEI, alphanum-only
        TptFile file = new TestFileBuilder()
                .row(values("20", VALID_LEI))
                .build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp, leis -> Map.of(), isins -> Map.of());

        List<Finding> out = svc.run(file, EmtTemplate.EXTERNAL_VALIDATION, settings, () -> false);
        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE/20/");
    }

    @Test
    void eptLeiHitWithType9(@TempDir Path tmp) {
        // EPT: field 14 polymorphic identifier; type "9" maps to LEI per spec convention
        TptFile file = new TestFileBuilder()
                .row(values("14", VALID_LEI, "15", "9"))
                .build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp, leis -> Map.of(), isins -> Map.of());

        List<Finding> out = svc.run(file, EptTemplate.EXTERNAL_VALIDATION, settings, () -> false);
        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE/14/15");
    }

    @Test
    void emptyConfigSkipsTheServiceEntirely(@TempDir Path tmp) {
        // Sanity check: an empty config short-circuits and produces no findings even when the
        // file would otherwise match TPT-style columns.
        TptFile file = new TestFileBuilder()
                .row(values("47", VALID_LEI, "48", "1"))
                .build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp, leis -> Map.of(), isins -> Map.of());

        List<Finding> out = svc.run(file, ExternalValidationConfig.empty(), settings, () -> false);
        assertThat(out).isEmpty();
    }
}
