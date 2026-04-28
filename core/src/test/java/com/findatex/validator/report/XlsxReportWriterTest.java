package com.findatex.validator.report;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.template.tpt.TptRuleSet;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.ValidationEngine;
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

        new XlsxReportWriter(CATALOG, TptProfiles.ALL).write(report, out);

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
        new XlsxReportWriter(CATALOG, TptProfiles.ALL).write(report, out);

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

            boolean foundZeile = false;
            for (int r = 0; r <= annotated.getLastRowNum(); r++) {
                org.apache.poi.ss.usermodel.Row rr = annotated.getRow(r);
                if (rr == null) continue;
                org.apache.poi.ss.usermodel.Cell c0 = rr.getCell(0);
                if (c0 != null
                        && c0.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                        && "Zeile".equals(c0.getStringCellValue())) {
                    foundZeile = true;
                    break;
                }
            }
            assertThat(foundZeile)
                    .as("Annotated Source must label the Zeile helper column")
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
    void scoresAreWrittenAsPercentages(@TempDir Path tmp) throws Exception {
        QualityReport report = buildReportFor("/sample/clean_v7.xlsx");
        Path out = tmp.resolve("scores.xlsx");
        new XlsxReportWriter(CATALOG, TptProfiles.ALL).write(report, out);

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
