package com.tpt.validator.spec;

import com.tpt.validator.template.api.ProfileKey;
import com.tpt.validator.template.tpt.TptProfiles;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpecLoaderCornerCasesTest {

    @Test
    void picksFallbackSheetWhenNamedSheetMissing() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            // Sheet name doesn't match "TPT V7.0" — loader should fall back to sheet 0.
            Sheet s = wb.createSheet("Random");
            populateMinimalSpecRow(s, 7, "12_CIC_code_of_the_instrument", "Position / InstrumentCIC");
            SpecCatalog c = SpecLoader.load(wb);
            assertThat(c.byNumKey("12")).isPresent();
        }
    }

    @Test
    void resolvesNumericCellWithIntegerValue() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT V7.0");
            // Header rows 1..7 are the disclaimer/header area in real spec; SpecLoader starts at row 8 (1-indexed).
            Row data = s.createRow(7);                          // 0-indexed row 7 = 1-indexed row 8
            data.createCell(0).setCellValue(42);                // NUM_DATA as numeric
            data.createCell(1).setCellValue("Position / Test"); // path
            data.createCell(2).setCellValue("def");
            data.createCell(10).setCellValue("M");              // K column = flag
            // Loader will read NUM_DATA as "42" (stripped trailing zero).
            SpecCatalog c = SpecLoader.load(wb);
            assertThat(c.byNumData("42")).isPresent();
        }
    }

    @Test
    void resolvesFormulaCellWithCachedNumericResult() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT V7.0");
            Row data = s.createRow(7);
            // Encode the NUM_DATA via a formula
            Cell num = data.createCell(0);
            num.setCellFormula("100+1");
            wb.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(num);

            data.createCell(1).setCellValue("Position / Formula");
            data.createCell(10).setCellValue("M");
            SpecCatalog c = SpecLoader.load(wb);
            assertThat(c.byNumKey("101")).isPresent();
        }
    }

    @Test
    void resolvesBooleanCell() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT V7.0");
            Row data = s.createRow(7);
            data.createCell(0).setCellValue("12_test");
            data.createCell(1).setCellValue("Position / Bool");
            data.createCell(10).setCellValue("M");
            // CIC applicability column (12 = column index 11) — set via boolean
            Cell bool = data.createCell(11);
            bool.setCellValue(true);
            SpecCatalog c = SpecLoader.load(wb);
            assertThat(c.byNumKey("12").get().applicableCic()).contains("CIC0");
        }
    }

    @Test
    void skipsRowsWithoutNumDataAndPath() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT V7.0");
            // Only blank rows in the data area
            s.createRow(7).createCell(0).setCellValue("");
            s.createRow(8);                                     // entirely blank
            populateMinimalSpecRow(s, 9, "12_test", "Position / OK");
            SpecCatalog c = SpecLoader.load(wb);
            assertThat(c.fields()).hasSize(1);
            assertThat(c.byNumKey("12")).isPresent();
        }
    }

    @Test
    void keepsFieldRowsWithBlankPathButValidNumDataLabel() throws Exception {
        // Real spec: row 1000_TPT_Version has only a single space in the path column.
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT V7.0");
            Row data = s.createRow(7);
            data.createCell(0).setCellValue("1000_TPT_Version");
            data.createCell(1).setCellValue(" ");                // blank path
            data.createCell(10).setCellValue("M");
            SpecCatalog c = SpecLoader.load(wb);
            assertThat(c.byNumKey("1000")).isPresent();
        }
    }

    @Test
    void dropsSectionHeaderRowsWithoutPath() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT V7.0");
            // A genuine section-header row: NUM_DATA = "Section Title", path empty.
            Row sectionHeader = s.createRow(7);
            sectionHeader.createCell(0).setCellValue("Portfolio Characteristics");
            populateMinimalSpecRow(s, 8, "12_test", "Position / Real");
            SpecCatalog c = SpecLoader.load(wb);
            assertThat(c.fields()).hasSize(1);
            assertThat(c.byNumKey("12")).isPresent();
        }
    }

    @Test
    void mergesFlagsTakingTheStrictest() throws Exception {
        // EIOPA + IORP cells contain text codes — the loader should merge them as M (presence).
        // Column indices (0-based): K=10 (Solvency II), AC=28 (NW675), AD=29 (SST),
        // AE=30 (IORP), AF=31 (EIOPA pos).
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT V7.0");
            Row data = s.createRow(7);
            data.createCell(0).setCellValue("12_test");
            data.createCell(1).setCellValue("Position / Test");
            data.createCell(10).setCellValue("O");      // SOLVENCY_II = O
            data.createCell(28).setCellValue("C");      // NW675 = C
            data.createCell(29).setCellValue("M");      // SST = M
            data.createCell(30).setCellValue("M");      // IORP = M
            data.createCell(31).setCellValue("C0110 - X"); // EIOPA pos cell — presence
            SpecCatalog c = SpecLoader.load(wb);
            FieldSpec spec = c.byNumKey("12").orElseThrow();
            assertThat(spec.flag(TptProfiles.SOLVENCY_II)).isEqualTo(Flag.O);
            assertThat(spec.flag(TptProfiles.NW_675)).isEqualTo(Flag.C);
            assertThat(spec.flag(TptProfiles.SST)).isEqualTo(Flag.M);
            // IORP_EIOPA_ECB merges IORP=M with EIOPA=M → strictest is M.
            assertThat(spec.flag(TptProfiles.IORP_EIOPA_ECB)).isEqualTo(Flag.M);
        }
    }

    @Test
    void sstFlagIsParsedIndependentlyOfNw675() throws Exception {
        // Confirms the SST column (AD = index 29) is not confused with NW675 (AC = 28)
        // or IORP (AE = 30). Each column gets its own flag.
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT V7.0");
            Row data = s.createRow(7);
            data.createCell(0).setCellValue("99_test");
            data.createCell(1).setCellValue("Position / Test");
            data.createCell(10).setCellValue("M");       // SOLVENCY_II
            data.createCell(28).setCellValue("");        // NW675 blank
            data.createCell(29).setCellValue("C");       // SST = C
            data.createCell(30).setCellValue("");        // IORP blank
            SpecCatalog c = SpecLoader.load(wb);
            FieldSpec spec = c.byNumKey("99").orElseThrow();
            assertThat(spec.flag(TptProfiles.NW_675)).isEqualTo(Flag.UNKNOWN);
            assertThat(spec.flag(TptProfiles.SST)).isEqualTo(Flag.C);
            assertThat(spec.flag(TptProfiles.IORP_EIOPA_ECB)).isEqualTo(Flag.UNKNOWN);
        }
    }

    @Test
    void allEiopaProfileColumnsMapToMandatory() throws Exception {
        // Iterate every EIOPA column (AF, AG, AH, AI = indices 31, 32, 33, 34) and confirm
        // that any non-blank value lifts the IORP profile to M.
        for (int col : new int[]{31, 32, 33, 34}) {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet s = wb.createSheet("TPT V7.0");
                Row data = s.createRow(7);
                data.createCell(0).setCellValue("12_test");
                data.createCell(1).setCellValue("Position / Test");
                data.createCell(col).setCellValue("C0123 - foo");
                SpecCatalog c = SpecLoader.load(wb);
                assertThat(c.byNumKey("12").get().flag(TptProfiles.IORP_EIOPA_ECB))
                        .as("col %d should lift profile to M", col)
                        .isEqualTo(Flag.M);
            }
        }
    }

    @Test
    void detectsAllCicApplicabilityColumns() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT V7.0");
            Row data = s.createRow(7);
            data.createCell(0).setCellValue("12_test");
            data.createCell(1).setCellValue("Position / Test");
            // mark CIC0..F by writing "x" in columns 11..26
            for (int col = 11; col <= 26; col++) {
                data.createCell(col).setCellValue("x");
            }
            SpecCatalog c = SpecLoader.load(wb);
            FieldSpec spec = c.byNumKey("12").orElseThrow();
            assertThat(spec.applicableCic()).hasSize(16);
            assertThat(spec.appliesToAllCic()).isTrue();
        }
    }

    @Test
    void handlesEmptyWorkbookGracefully() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("Empty");
            SpecCatalog c = SpecLoader.load(wb);
            assertThat(c.fields()).isEmpty();
        }
    }

    private static void populateMinimalSpecRow(Sheet s, int rowIdx, String numData, String path) {
        Row r = s.createRow(rowIdx);
        r.createCell(0).setCellValue(numData);
        r.createCell(1).setCellValue(path);
        r.createCell(10).setCellValue("M");
    }
}
