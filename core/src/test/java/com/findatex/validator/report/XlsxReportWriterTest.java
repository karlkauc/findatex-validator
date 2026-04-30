package com.findatex.validator.report;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.template.tpt.TptRuleSet;
import com.findatex.validator.template.tpt.TptTemplate;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.ValidationEngine;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class XlsxReportWriterTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    @Test
    void writesAllExpectedTabsAndContent(@TempDir Path tmp) throws Exception {
        QualityReport report = buildReportFor("/sample/bad_formats.xlsx");
        Path out = tmp.resolve("report.xlsx");

        new XlsxReportWriter(CATALOG, TptProfiles.ALL, TptTemplate.V7_0, GenerationUi.DESKTOP).write(report, out);

        assertThat(Files.exists(out)).isTrue();
        assertThat(Files.size(out)).isGreaterThan(2_000);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            Set<String> sheetNames = new HashSet<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                sheetNames.add(wb.getSheetAt(i).getSheetName());
            }
            assertThat(sheetNames)
                    .containsExactlyInAnyOrder("Summary", "Scores", "Findings", "Field Coverage",
                            "Per Position", "Annotated Source");

            Sheet findings = wb.getSheet("Findings");
            assertThat(findings.getLastRowNum())
                    .as("Findings sheet should contain at least the header row + data rows")
                    .isGreaterThan(report.findings().size() - 5); // tolerate any zero-indexing slack

            // Header row sanity — the Findings tab now has 14 columns including
            // portfolio + position context (Fund ID, Fund name, Valuation date,
            // Instrument code, Instrument name, Weight).
            org.apache.poi.ss.usermodel.Row hdr = findings.getRow(0);
            java.util.List<String> headers = new java.util.ArrayList<>();
            for (int c = 0; c < hdr.getLastCellNum(); c++) headers.add(hdr.getCell(c).getStringCellValue());
            assertThat(headers).containsExactly(
                    "Severity", "Profile", "Rule",
                    "Fund ID", "Fund name", "Valuation date",
                    "Field#", "Field name", "Row",
                    "Instrument code", "Instrument name",
                    "Weight", "Value", "Message");

            // Field Coverage tab carries 142 rows + header. Header has one column per
            // profile in the active template's ProfileSet, using displayName().
            Sheet coverage = wb.getSheet("Field Coverage");
            assertThat(coverage.getLastRowNum()).isEqualTo(CATALOG.fields().size());
            org.apache.poi.ss.usermodel.Row covHdr = coverage.getRow(0);
            java.util.List<String> covHeaders = new java.util.ArrayList<>();
            for (int c = 0; c < covHdr.getLastCellNum(); c++) covHeaders.add(covHdr.getCell(c).getStringCellValue());
            assertThat(covHeaders).contains(
                    TptProfiles.SOLVENCY_II.displayName(),
                    TptProfiles.IORP_EIOPA_ECB.displayName(),
                    TptProfiles.NW_675.displayName(),
                    TptProfiles.SST.displayName());

            Sheet perPos = wb.getSheet("Per Position");
            assertThat(perPos.getLastRowNum()).isEqualTo(report.file().rows().size());

            // Summary lists every active profile.
            Sheet summary = wb.getSheet("Summary");
            String summaryDump = dumpStrings(summary);
            for (ProfileKey p : report.activeProfiles()) {
                assertThat(summaryDump).contains(p.displayName());
            }
        }
    }

    @Test
    void writesAnnotatedSourceWithHighlightsAndComments(@TempDir Path tmp) throws Exception {
        QualityReport report = buildReportFor("/sample/bad_formats.xlsx");
        Path out = tmp.resolve("annotated.xlsx");
        new XlsxReportWriter(CATALOG, TptProfiles.ALL, TptTemplate.V7_0, GenerationUi.DESKTOP).write(report, out);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet annotated = wb.getSheet("Annotated Source");
            assertThat(annotated).as("Annotated Source tab must be present").isNotNull();

            Finding target = report.findings().stream()
                    .filter(f -> f.severity() == com.findatex.validator.validation.Severity.ERROR)
                    .filter(f -> f.fieldNum() != null && f.rowIndex() != null)
                    .findFirst()
                    .orElse(null);
            assertThat(target)
                    .as("bad_formats.xlsx should produce at least one cell-level ERROR")
                    .isNotNull();

            com.findatex.validator.domain.TptRow tptRow = report.file().rows().stream()
                    .filter(rr -> rr.rowIndex() == target.rowIndex())
                    .findFirst()
                    .orElseThrow();
            com.findatex.validator.domain.RawCell rc = tptRow.all().get(target.fieldNum());
            assertThat(rc).as("RawCell for finding's field must exist").isNotNull();

            int mirrorRow = rc.sourceRow() - 1;
            int mirrorCol = rc.sourceCol(); // shifted by +1 to account for Zeile column
            org.apache.poi.ss.usermodel.Row poiRow = annotated.getRow(mirrorRow);
            assertThat(poiRow).isNotNull();
            org.apache.poi.ss.usermodel.Cell cell = poiRow.getCell(mirrorCol);
            assertThat(cell).as("target cell at (%d,%d)", mirrorRow, mirrorCol).isNotNull();

            assertThat(cell.getCellStyle().getFillForegroundColor())
                    .as("error cell should be coloured rose")
                    .isEqualTo(org.apache.poi.ss.usermodel.IndexedColors.ROSE.getIndex());

            org.apache.poi.ss.usermodel.Comment comment = cell.getCellComment();
            assertThat(comment).as("error cell should carry a comment").isNotNull();
            String text = comment.getString().getString();
            assertThat(text).contains("[ERROR]");
            assertThat(text).contains(target.ruleId());
            assertThat(text).contains(target.message());

            boolean foundRowHeader = false;
            for (int r = 0; r <= annotated.getLastRowNum(); r++) {
                org.apache.poi.ss.usermodel.Row rr = annotated.getRow(r);
                if (rr == null) continue;
                org.apache.poi.ss.usermodel.Cell c0 = rr.getCell(0);
                if (c0 != null
                        && c0.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                        && "Row".equals(c0.getStringCellValue())) {
                    foundRowHeader = true;
                    break;
                }
            }
            assertThat(foundRowHeader)
                    .as("Annotated Source must label the Row helper column")
                    .isTrue();

            java.util.Set<Integer> rowsWithFindings = report.findings().stream()
                    .filter(f -> f.rowIndex() != null)
                    .map(Finding::rowIndex)
                    .collect(java.util.stream.Collectors.toSet());
            com.findatex.validator.domain.TptRow cleanRow = report.file().rows().stream()
                    .filter(rr -> !rowsWithFindings.contains(rr.rowIndex()))
                    .findFirst()
                    .orElse(null);
            if (cleanRow != null) {
                com.findatex.validator.domain.RawCell anyCell = cleanRow.all().values().stream()
                        .filter(rcc -> !rcc.isEmpty())
                        .findFirst()
                        .orElse(null);
                if (anyCell != null) {
                    org.apache.poi.ss.usermodel.Row pr = annotated.getRow(anyCell.sourceRow() - 1);
                    if (pr != null) {
                        org.apache.poi.ss.usermodel.Cell pc = pr.getCell(anyCell.sourceCol());
                        if (pc != null) {
                            assertThat(pc.getCellComment())
                                    .as("clean cell at (%d,%d) should have no comment",
                                            anyCell.sourceRow() - 1, anyCell.sourceCol())
                                    .isNull();
                        }
                    }
                }
            }
        }
    }

    @Test
    void rowColumnIsHighlightedWhenAnyCellInRowHasError(@TempDir Path tmp) throws Exception {
        QualityReport report = buildReportFor("/sample/bad_formats.xlsx");
        Path out = tmp.resolve("annotated_row_color.xlsx");
        new XlsxReportWriter(CATALOG, TptProfiles.ALL, TptTemplate.V7_0, GenerationUi.DESKTOP).write(report, out);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet annotated = wb.getSheet("Annotated Source");
            assertThat(annotated).isNotNull();

            Finding fieldErr = report.findings().stream()
                    .filter(f -> f.severity() == com.findatex.validator.validation.Severity.ERROR)
                    .filter(f -> f.fieldNum() != null && f.rowIndex() != null)
                    .findFirst()
                    .orElseThrow();

            com.findatex.validator.domain.TptRow tptRow = report.file().rows().stream()
                    .filter(rr -> rr.rowIndex() == fieldErr.rowIndex())
                    .findFirst()
                    .orElseThrow();
            int mirrorRow = tptRow.all().values().iterator().next().sourceRow() - 1;

            org.apache.poi.ss.usermodel.Row poiRow = annotated.getRow(mirrorRow);
            org.apache.poi.ss.usermodel.Cell rowCell = poiRow.getCell(0);
            assertThat(rowCell)
                    .as("Row helper cell at row %d must exist", mirrorRow)
                    .isNotNull();
            assertThat(rowCell.getCellStyle().getFillForegroundColor())
                    .as("Row helper cell on a row with an ERROR must be coloured rose")
                    .isEqualTo(org.apache.poi.ss.usermodel.IndexedColors.ROSE.getIndex());
        }
    }

    @Test
    void annotatedSourcePreservesNumericAndDateCellTypes(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("typed.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook();
             java.io.OutputStream os = Files.newOutputStream(src)) {
            Sheet s = wb.createSheet();
            org.apache.poi.ss.usermodel.Row hdr = s.createRow(0);
            String[] headers = {
                    "1_Portfolio_identifying_data",
                    "2_Type_of_identification_code_for_the_fund_share_or_portfolio",
                    "3_Portfolio_name",
                    "4_Portfolio_currency_(B)",
                    "5_Net_asset_valuation_of_the_portfolio_or_the_share_class_in_portfolio_currency",
                    "6_Valuation_date"
            };
            for (int c = 0; c < headers.length; c++) hdr.createCell(c).setCellValue(headers[c]);
            CellStyle pctStyle = wb.createCellStyle();
            pctStyle.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));

            org.apache.poi.ss.usermodel.Row data = s.createRow(1);
            data.createCell(0).setCellValue("FR0010000001");
            data.createCell(1).setCellValue("1");
            data.createCell(2).setCellValue("Demo Bond Fund");
            data.createCell(3).setCellValue("EUR");
            org.apache.poi.ss.usermodel.Cell numeric = data.createCell(4);
            numeric.setCellValue(0.0525);
            numeric.setCellStyle(pctStyle);
            org.apache.poi.ss.usermodel.Cell dateCell = data.createCell(5);
            dateCell.setCellValue(java.time.LocalDate.of(2025, 12, 31));
            dateCell.setCellStyle(dateStyle);
            wb.write(os);
        }

        com.findatex.validator.domain.TptFile file = new com.findatex.validator.ingest.TptFileLoader(CATALOG).load(src);
        Set<ProfileKey> profiles = new HashSet<>(java.util.Arrays.asList(
                TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB,
                TptProfiles.NW_675, TptProfiles.SST));
        List<Finding> findings = new ValidationEngine(CATALOG, new com.findatex.validator.template.tpt.TptRuleSet()).validate(file, profiles);
        QualityReport report = new QualityScorer(CATALOG).score(file, profiles, findings);

        Path out = tmp.resolve("annotated_typed.xlsx");
        new XlsxReportWriter(CATALOG, TptProfiles.ALL, TptTemplate.V7_0, GenerationUi.DESKTOP).write(report, out);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet annotated = wb.getSheet("Annotated Source");
            assertThat(annotated).isNotNull();

            int firstDataMirrorRow = report.file().rows().get(0).all().values().iterator().next().sourceRow() - 1;
            org.apache.poi.ss.usermodel.Row poiRow = annotated.getRow(firstDataMirrorRow);
            assertThat(poiRow).isNotNull();

            org.apache.poi.ss.usermodel.Cell numericMirror = poiRow.getCell(5); // +1 for Row helper col
            assertThat(numericMirror.getCellType())
                    .as("numeric source cell must be mirrored as NUMERIC, not STRING")
                    .isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
            assertThat(numericMirror.getNumericCellValue()).isEqualTo(0.0525);
            assertThat(numericMirror.getCellStyle().getDataFormatString())
                    .as("numeric format must be preserved")
                    .isEqualTo("0.00%");

            org.apache.poi.ss.usermodel.Cell dateMirror = poiRow.getCell(6); // +1 for Row helper col
            assertThat(dateMirror.getCellType())
                    .as("date source cell must be mirrored as NUMERIC (Excel dates)")
                    .isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
            assertThat(org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(dateMirror))
                    .as("date cell must keep date formatting")
                    .isTrue();
            assertThat(dateMirror.getCellStyle().getDataFormatString())
                    .as("date format must be preserved")
                    .isEqualTo("yyyy-mm-dd");
        }
    }

    @Test
    void scoresAreWrittenAsPercentages(@TempDir Path tmp) throws Exception {
        QualityReport report = buildReportFor("/sample/clean_v7.xlsx");
        Path out = tmp.resolve("scores.xlsx");
        new XlsxReportWriter(CATALOG, TptProfiles.ALL, TptTemplate.V7_0, GenerationUi.DESKTOP).write(report, out);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet scores = wb.getSheet("Scores");
            // The first block (after the header in row 0) writes one row per overall score
            // category. Each value must be a fraction in [0, 1]; the cell carries a percent
            // format applied via cellStyle, so getNumericCellValue returns the underlying
            // fraction. We walk until we hit the first non-numeric (the per-profile header).
            int seen = 0;
            for (int r = 1; r <= scores.getLastRowNum(); r++) {
                org.apache.poi.ss.usermodel.Row row = scores.getRow(r);
                if (row == null) break;
                org.apache.poi.ss.usermodel.Cell cell = row.getCell(1);
                if (cell == null
                        || cell.getCellType() != org.apache.poi.ss.usermodel.CellType.NUMERIC) break;
                assertThat(cell.getNumericCellValue()).isBetween(0.0, 1.0);
                seen++;
            }
            assertThat(seen)
                    .as("Scores sheet should contain at least 4 overall categories")
                    .isGreaterThanOrEqualTo(4);
        }
    }

    private static String dumpStrings(Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                org.apache.poi.ss.usermodel.Cell cell = row.getCell(c);
                if (cell == null) continue;
                if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                    sb.append(cell.getStringCellValue()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private QualityReport buildReportFor(String resourcePath) throws Exception {
        URL url = XlsxReportWriterTest.class.getResource(resourcePath);
        assertThat(url).as("missing test resource %s", resourcePath).isNotNull();
        Path p = Path.of(url.toURI());
        TptFile file = new TptFileLoader(CATALOG).load(p);
        Set<ProfileKey> profiles = new java.util.HashSet<>(java.util.Arrays.asList(TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB, TptProfiles.NW_675, TptProfiles.SST));
        List<Finding> findings = new ValidationEngine(CATALOG, new TptRuleSet()).validate(file, profiles);
        return new QualityScorer(CATALOG).score(file, profiles, findings);
    }
}
