package com.tpt.validator.spec;

import com.tpt.validator.template.api.ProfileKey;
import com.tpt.validator.template.api.TemplateId;
import com.tpt.validator.template.api.TemplateRegistry;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestDrivenSpecLoaderTest {

    /**
     * Synthetic manifest with intentionally non-TPT column indices to prove the loader is truly
     * config-driven and not implicitly hard-coded to the TPT layout. 3 fields, 1 profile, 1 CIC.
     */
    private static SpecManifest syntheticManifest() {
        return new SpecManifest(
                "TEST",
                "v0.1",
                "2026-01-01",
                "S1",
                3,                                       // first data row (1-based)
                new SpecManifest.Columns(1, 2, 3, 4, 5, 6),
                new SpecManifest.ApplicabilityColumns("CIC", 7, 7, List.of("CIC2")),
                List.of(new SpecManifest.ProfileColumn(
                        "BASIC", "Basic profile", 6, null, "flag")));
    }

    /**
     * Synthetic workbook mirroring the manifest above:
     *   row 1: title (skipped, before firstDataRow=3)
     *   row 2: header (skipped, before firstDataRow=3)
     *   row 3: field "12_field_a" — applies to CIC2, BASIC=M
     *   row 4: field "13_field_b" — no CIC mapping, BASIC=O
     *   row 5: section header (only NUM_DATA, no path) — must be skipped by looksLikeFieldLabel
     *   row 6: field "14_field_c" — CIC2, BASIC=C
     */
    private static Workbook syntheticWorkbook() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("S1");
        // Rows 0,1 are pre-header padding (firstDataRow=3 means we start scanning from row index 2).
        s.createRow(0).createCell(0).setCellValue("Disclaimer");
        s.createRow(1).createCell(0).setCellValue("Header row");

        Row r2 = s.createRow(2);     // row 3 (1-based) — first field
        r2.createCell(0).setCellValue("12_field_a");
        r2.createCell(1).setCellValue("Position / A");
        r2.createCell(2).setCellValue("definition A");
        r2.createCell(3).setCellValue("numeric (1..7)");
        r2.createCell(4).setCellValue("comment A");
        r2.createCell(5).setCellValue("M");
        r2.createCell(6).setCellValue("x");

        Row r3 = s.createRow(3);     // row 4 — field with no CIC mapping
        r3.createCell(0).setCellValue("13_field_b");
        r3.createCell(1).setCellValue("Position / B");
        r3.createCell(2).setCellValue("definition B");
        r3.createCell(3).setCellValue("ISO 4217");
        r3.createCell(4).setCellValue("comment B");
        r3.createCell(5).setCellValue("O");
        // Column 7 (CIC2) intentionally blank.

        Row r4 = s.createRow(4);     // row 5 — section header (no path, no underscore prefix)
        r4.createCell(0).setCellValue("Sub-section");

        Row r5 = s.createRow(5);     // row 6 — third field
        r5.createCell(0).setCellValue("14_field_c");
        r5.createCell(1).setCellValue("Position / C");
        r5.createCell(2).setCellValue("definition C");
        r5.createCell(3).setCellValue("alpha (3)");
        r5.createCell(4).setCellValue("comment C");
        r5.createCell(5).setCellValue("C");
        r5.createCell(6).setCellValue("x");

        return wb;
    }

    @Test
    void parsesSyntheticThreeFieldSpec() {
        ManifestDrivenSpecLoader loader = new ManifestDrivenSpecLoader(syntheticManifest(), "/unused");
        try (Workbook wb = syntheticWorkbook()) {
            SpecCatalog cat = loader.loadFromWorkbook(wb);
            assertThat(cat.fields()).hasSize(3);
            assertThat(cat.fields()).extracting(FieldSpec::numKey).containsExactly("12", "13", "14");
            assertThat(cat.fields()).extracting(FieldSpec::definition)
                    .containsExactly("definition A", "definition B", "definition C");
        } catch (Exception e) {
            throw new AssertionError("Workbook close failed", e);
        }
    }

    @Test
    void respectsApplicabilityScope() {
        ManifestDrivenSpecLoader loader = new ManifestDrivenSpecLoader(syntheticManifest(), "/unused");
        try (Workbook wb = syntheticWorkbook()) {
            SpecCatalog cat = loader.loadFromWorkbook(wb);
            FieldSpec a = cat.byNumKey("12").orElseThrow();
            FieldSpec b = cat.byNumKey("13").orElseThrow();
            // Field A has CIC2 applicability → CicApplicabilityScope listing CIC2.
            assertThat(a.applicabilityScope()).isInstanceOf(CicApplicabilityScope.class);
            assertThat(a.applicableCic()).containsExactly("CIC2");
            // Field B has no CIC cell filled → falls back to EmptyApplicabilityScope.
            assertThat(b.applicabilityScope()).isInstanceOf(EmptyApplicabilityScope.class);
            assertThat(b.applicableCic()).isEmpty();
        } catch (Exception e) {
            throw new AssertionError("Workbook close failed", e);
        }
    }

    @Test
    void mapsProfileFlagsByCode() {
        ManifestDrivenSpecLoader loader = new ManifestDrivenSpecLoader(syntheticManifest(), "/unused");
        try (Workbook wb = syntheticWorkbook()) {
            SpecCatalog cat = loader.loadFromWorkbook(wb);
            assertThat(cat.byNumKey("12").orElseThrow().flag("BASIC")).isEqualTo(Flag.M);
            assertThat(cat.byNumKey("13").orElseThrow().flag("BASIC")).isEqualTo(Flag.O);
            assertThat(cat.byNumKey("14").orElseThrow().flag("BASIC")).isEqualTo(Flag.C);
        } catch (Exception e) {
            throw new AssertionError("Workbook close failed", e);
        }
    }

    @Test
    void bundledTptV7ManifestProducesCatalogIdenticalToLegacyLoader() {
        // End-to-end equivalence: the manifest-driven loader must yield the same field count
        // and the same per-field flag values for the bundled TPT V7 spec.
        TemplateRegistry.init();
        SpecCatalog viaLegacy = SpecLoader.loadBundled();
        SpecCatalog viaManifest = TemplateRegistry.of(TemplateId.TPT)
                .specLoaderFor(TemplateRegistry.of(TemplateId.TPT).latest())
                .load();

        assertThat(viaManifest.fields()).hasSameSizeAs(viaLegacy.fields());
        ProfileKey solvency = com.tpt.validator.template.tpt.TptProfiles.SOLVENCY_II;
        // Spot-check a handful of fields where Solvency II flag is well-known.
        for (FieldSpec legacy : viaLegacy.fields()) {
            FieldSpec manifest = viaManifest.byNumKey(legacy.numKey()).orElseThrow();
            assertThat(manifest.flag(solvency))
                    .as("Solvency II flag for field %s", legacy.numKey())
                    .isEqualTo(legacy.flag(solvency));
        }
    }
}
