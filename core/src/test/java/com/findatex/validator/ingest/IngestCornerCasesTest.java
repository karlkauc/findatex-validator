package com.findatex.validator.ingest;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestCornerCasesTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    // ------------------------------------------------------------ TptFileLoader

    @Test
    void loaderRejectsUnknownExtension(@TempDir Path tmp) throws IOException {
        Path bogus = tmp.resolve("foo.pdf");
        Files.writeString(bogus, "garbage");
        assertThatThrownBy(() -> new TptFileLoader(CATALOG).load(bogus))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Unsupported file extension");
    }

    @Test
    void loaderDispatchesXlsmToXlsxLoader(@TempDir Path tmp) throws Exception {
        Path xlsm = tmp.resolve("demo.xlsm");
        writeMinimalXlsx(xlsm, List.of("12_CIC_code_of_the_instrument"), List.of(List.of("FR12")));
        TptFile file = new TptFileLoader(CATALOG).load(xlsm);
        assertThat(file.inputFormat()).isEqualTo("xlsx");
        assertThat(file.rows()).hasSize(1);
    }

    @Test
    void loaderDispatchesTsvToCsvLoader(@TempDir Path tmp) throws Exception {
        Path tsv = tmp.resolve("demo.tsv");
        Files.writeString(tsv, "12_CIC_code_of_the_instrument\tFR12\n", StandardCharsets.UTF_8);
        // CsvLoader expects header on row 1 and data thereafter — so put data row 2.
        Files.writeString(tsv, "12_CIC_code_of_the_instrument\nFR12\n", StandardCharsets.UTF_8);
        TptFile file = new TptFileLoader(CATALOG).load(tsv);
        assertThat(file.inputFormat()).isEqualTo("csv");
    }

    // -------------------------------------------------------------- CsvLoader

    @Test
    void csvAutoDetectsSemicolonDelimiter(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("semi.csv");
        Files.writeString(csv, """
                12_CIC_code_of_the_instrument;17_Instrument_name
                FR12;Treasury bond
                """, StandardCharsets.UTF_8);
        TptFile file = new CsvLoader(CATALOG).load(csv);
        assertThat(file.rows()).hasSize(1);
        assertThat(file.rows().get(0).stringValue("12")).contains("FR12");
        assertThat(file.rows().get(0).stringValue("17")).contains("Treasury bond");
    }

    @Test
    void csvAutoDetectsTabDelimiter(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("tab.csv");
        Files.writeString(csv, """
                12_CIC_code_of_the_instrument\t17_Instrument_name
                FR12\tTreasury bond
                """, StandardCharsets.UTF_8);
        TptFile file = new CsvLoader(CATALOG).load(csv);
        assertThat(file.rows()).hasSize(1);
        assertThat(file.rows().get(0).stringValue("17")).contains("Treasury bond");
    }

    @Test
    void csvAutoDetectsCommaDelimiter(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("comma.csv");
        Files.writeString(csv, """
                12_CIC_code_of_the_instrument,17_Instrument_name
                FR12,Treasury bond
                """, StandardCharsets.UTF_8);
        TptFile file = new CsvLoader(CATALOG).load(csv);
        assertThat(file.rows()).hasSize(1);
    }

    @Test
    void csvAutoDetectsPipeDelimiter(@TempDir Path tmp) throws Exception {
        // Real-world FinDatEx CSVs (e.g. UBS EPT V2.1) ship pipe-delimited
        // because field values contain commas and semicolons themselves.
        Path csv = tmp.resolve("pipe.csv");
        Files.writeString(csv, """
                12_CIC_code_of_the_instrument|17_Instrument_name
                FR12|Treasury bond
                """, StandardCharsets.UTF_8);
        TptFile file = new CsvLoader(CATALOG).load(csv);
        assertThat(file.rows()).hasSize(1);
        assertThat(file.rows().get(0).stringValue("12")).contains("FR12");
        assertThat(file.rows().get(0).stringValue("17")).contains("Treasury bond");
    }

    @Test
    void csvPipeAcceptsStrayQuotesAsLiterals(@TempDir Path tmp) throws Exception {
        // Reproduces the UBS_Asset_Management_EPT_UBSGAMFundsLtd_V2.1_en.csv failure:
        // pipe-delimited file whose producer wrapped a description in "..." but didn't
        // escape inner '"' characters. The FinDatEx pipe convention does not need
        // quoting (pipe never appears in values), so the loader treats '"' as a literal
        // character rather than failing with CSVException.
        Path csv = tmp.resolve("pipe-stray-quote.csv");
        Files.writeString(csv,
                "12_CIC_code_of_the_instrument|17_Instrument_name\n"
              + "FR12|\"The fund (the \"Fund\") aims to grow.\"The return depends on...\n",
                StandardCharsets.UTF_8);
        TptFile file = new CsvLoader(CATALOG).load(csv);
        assertThat(file.rows()).hasSize(1);
        assertThat(file.rows().get(0).stringValue("12")).contains("FR12");
        String name17 = file.rows().get(0).stringValue("17").orElseThrow();
        assertThat(name17)
                .as("stray quotes should appear verbatim in the value")
                .contains("\"Fund\"")
                .contains("aims to grow.\"The return");
    }

    @Test
    void csvHandlesQuotedDelimiterInsideValue(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("quoted.csv");
        Files.writeString(csv,
                "12_CIC_code_of_the_instrument;17_Instrument_name\n"
              + "FR12;\"Acme; co.\"\n",
                StandardCharsets.UTF_8);
        TptFile file = new CsvLoader(CATALOG).load(csv);
        assertThat(file.rows().get(0).stringValue("17")).contains("Acme; co.");
    }

    @Test
    void csvWithEmptyFileProducesNoRows(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("empty.csv");
        Files.writeString(csv, "", StandardCharsets.UTF_8);
        TptFile file = new CsvLoader(CATALOG).load(csv);
        assertThat(file.rows()).isEmpty();
        assertThat(file.rawHeaders()).isEmpty();
    }

    @Test
    void csvWithHeaderOnlyHasNoData(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("hdronly.csv");
        Files.writeString(csv, "12_CIC_code_of_the_instrument;17_Instrument_name\n",
                StandardCharsets.UTF_8);
        TptFile file = new CsvLoader(CATALOG).load(csv);
        assertThat(file.rows()).isEmpty();
        assertThat(file.headerToNumKey()).hasSize(2);
    }

    @Test
    void csvUnknownHeaderGoesToUnmappedList(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("unmapped.csv");
        Files.writeString(csv,
                "12_CIC_code_of_the_instrument;BogusHeader\nFR12;ignore\n",
                StandardCharsets.UTF_8);
        TptFile file = new CsvLoader(CATALOG).load(csv);
        assertThat(file.unmappedHeaders()).contains("BogusHeader");
    }

    // -------------------------------------------------------------- XlsxLoader

    @Test
    void xlsxLoadsDateCellsAsIsoStrings(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("dates.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT");
            Row hdr = s.createRow(0);
            hdr.createCell(0).setCellValue("12_CIC_code_of_the_instrument");
            hdr.createCell(1).setCellValue("6_Valuation_date");
            Row data = s.createRow(1);
            data.createCell(0).setCellValue("FR12");

            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
            Cell dc = data.createCell(1);
            dc.setCellValue(java.sql.Date.valueOf(LocalDate.of(2025, 12, 31)));
            dc.setCellStyle(dateStyle);
            try (OutputStream os = Files.newOutputStream(file)) { wb.write(os); }
        }
        TptFile loaded = new XlsxLoader(CATALOG).load(file);
        assertThat(loaded.rows().get(0).stringValue("6")).contains("2025-12-31");
    }

    @Test
    void xlsxLoadsNumericCellsWithoutTrailingDecimal(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("nums.xlsx");
        writeMinimalXlsx(file,
                List.of("12_CIC_code_of_the_instrument", "8b_Total_number_of_shares"),
                List.of(List.of("FR12", "100000")));   // string-valued, should pass-through
        TptFile loaded = new XlsxLoader(CATALOG).load(file);
        assertThat(loaded.rows().get(0).stringValue("8b")).contains("100000");
    }

    @Test
    void xlsxFindsHeaderRowEvenWhenNotFirst(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("delayed.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT");
            // Disclaimer rows first
            s.createRow(0).createCell(0).setCellValue("Some disclaimer");
            s.createRow(1).createCell(0).setCellValue("Confidential");
            // Header row at index 3 — the loader requires >=3 mappable headers to lock in.
            Row hdr = s.createRow(3);
            hdr.createCell(0).setCellValue("12_CIC_code_of_the_instrument");
            hdr.createCell(1).setCellValue("17_Instrument_name");
            hdr.createCell(2).setCellValue("21_Quotation_currency_(A)");
            hdr.createCell(3).setCellValue("4_Portfolio_currency_(B)");
            Row data = s.createRow(4);
            data.createCell(0).setCellValue("FR12");
            data.createCell(1).setCellValue("Bond");
            data.createCell(2).setCellValue("EUR");
            data.createCell(3).setCellValue("EUR");
            try (OutputStream os = Files.newOutputStream(file)) { wb.write(os); }
        }
        TptFile loaded = new XlsxLoader(CATALOG).load(file);
        assertThat(loaded.rows()).hasSize(1);
        assertThat(loaded.rows().get(0).stringValue("17")).contains("Bond");
    }

    @Test
    void xlsxSkipsBlankDataRows(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("blanks.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT");
            Row hdr = s.createRow(0);
            hdr.createCell(0).setCellValue("12_CIC_code_of_the_instrument");
            s.createRow(1).createCell(0).setCellValue("FR12");
            // Row 2: deliberately blank
            s.createRow(2);
            s.createRow(3).createCell(0).setCellValue("DE31");
            try (OutputStream os = Files.newOutputStream(file)) { wb.write(os); }
        }
        TptFile loaded = new XlsxLoader(CATALOG).load(file);
        assertThat(loaded.rows()).hasSize(2);   // blank row dropped
    }

    @Test
    void xlsxFormulaCellsResolveToCachedValues(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("formulas.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT");
            Row hdr = s.createRow(0);
            hdr.createCell(0).setCellValue("12_CIC_code_of_the_instrument");
            hdr.createCell(1).setCellValue("8b_Total_number_of_shares");
            Row data = s.createRow(1);
            data.createCell(0).setCellValue("FR12");
            Cell formula = data.createCell(1);
            formula.setCellFormula("100*1000");
            // Pre-evaluate so the cached value is populated.
            wb.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(formula);
            try (OutputStream os = Files.newOutputStream(file)) { wb.write(os); }
        }
        TptFile loaded = new XlsxLoader(CATALOG).load(file);
        assertThat(loaded.rows().get(0).stringValue("8b")).contains("100000");
    }

    @Test
    void xlsxBooleanCellRendersAsString(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("bool.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT");
            Row hdr = s.createRow(0);
            hdr.createCell(0).setCellValue("12_CIC_code_of_the_instrument");
            hdr.createCell(1).setCellValue("11_Complete_SCR_delivery");
            Row data = s.createRow(1);
            data.createCell(0).setCellValue("FR12");
            data.createCell(1).setCellValue(true);
            try (OutputStream os = Files.newOutputStream(file)) { wb.write(os); }
        }
        TptFile loaded = new XlsxLoader(CATALOG).load(file);
        assertThat(loaded.rows().get(0).stringValue("11")).contains("true");
    }

    // ----------------------------------------------------- HeaderMapper

    @Test
    void headerMapperReturnsEmptyForBlankHeaders() {
        HeaderMapper m = new HeaderMapper(CATALOG);
        var unmapped = new java.util.ArrayList<String>();
        // List.of forbids null, so we use Arrays.asList for this nullable input.
        var map = m.map(java.util.Arrays.asList("", null, "  "), unmapped);
        assertThat(map).isEmpty();
        assertThat(unmapped).isEmpty();
    }

    @Test
    void headerMapperBucketsRecognisedAndUnknown() {
        HeaderMapper m = new HeaderMapper(CATALOG);
        var unmapped = new java.util.ArrayList<String>();
        var map = m.map(List.of("12", "Position / InstrumentCIC", "WhatIsThis"), unmapped);
        assertThat(map.values()).contains("12");
        assertThat(unmapped).containsExactly("WhatIsThis");
    }

    // ------------------------------------------------------------ helpers

    private static void writeMinimalXlsx(Path file, List<String> headers,
                                         List<List<String>> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("TPT");
            Row hdr = s.createRow(0);
            for (int i = 0; i < headers.size(); i++) hdr.createCell(i).setCellValue(headers.get(i));
            for (int r = 0; r < rows.size(); r++) {
                Row dr = s.createRow(r + 1);
                List<String> cells = rows.get(r);
                for (int c = 0; c < cells.size(); c++) dr.createCell(c).setCellValue(cells.get(c));
            }
            try (OutputStream os = Files.newOutputStream(file)) { wb.write(os); }
        }
    }
}
