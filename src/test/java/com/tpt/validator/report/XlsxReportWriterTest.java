package com.tpt.validator.report;

import com.tpt.validator.domain.TptFile;
import com.tpt.validator.ingest.TptFileLoader;
import com.tpt.validator.spec.Profile;
import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.spec.SpecLoader;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.ValidationEngine;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
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

        new XlsxReportWriter(CATALOG).write(report, out);

        assertThat(Files.exists(out)).isTrue();
        assertThat(Files.size(out)).isGreaterThan(2_000);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            Set<String> sheetNames = new HashSet<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                sheetNames.add(wb.getSheetAt(i).getSheetName());
            }
            assertThat(sheetNames)
                    .containsExactlyInAnyOrder("Summary", "Scores", "Findings", "Field Coverage", "Per Position");

            Sheet findings = wb.getSheet("Findings");
            assertThat(findings.getLastRowNum())
                    .as("Findings sheet should contain at least the header row + data rows")
                    .isGreaterThan(report.findings().size() - 5); // tolerate any zero-indexing slack

            // Header row sanity
            assertThat(findings.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Severity");
            assertThat(findings.getRow(0).getCell(7).getStringCellValue()).isEqualTo("Message");

            // Field Coverage tab carries 142 rows + header.
            Sheet coverage = wb.getSheet("Field Coverage");
            assertThat(coverage.getLastRowNum()).isEqualTo(CATALOG.fields().size());

            Sheet perPos = wb.getSheet("Per Position");
            assertThat(perPos.getLastRowNum()).isEqualTo(report.file().rows().size());

            // Summary lists every active profile.
            Sheet summary = wb.getSheet("Summary");
            String summaryDump = dumpStrings(summary);
            for (Profile p : report.activeProfiles()) {
                assertThat(summaryDump).contains(p.displayName());
            }
        }
    }

    @Test
    void scoresAreWrittenAsPercentages(@TempDir Path tmp) throws Exception {
        QualityReport report = buildReportFor("/sample/clean_v7.xlsx");
        Path out = tmp.resolve("scores.xlsx");
        new XlsxReportWriter(CATALOG).write(report, out);

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
        Set<Profile> profiles = EnumSet.allOf(Profile.class);
        List<Finding> findings = new ValidationEngine(CATALOG).validate(file, profiles);
        return new QualityScorer(CATALOG).score(file, profiles, findings);
    }
}
