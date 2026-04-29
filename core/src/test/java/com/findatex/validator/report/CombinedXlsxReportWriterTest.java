package com.findatex.validator.report;

import com.findatex.validator.batch.BatchFileStatus;
import com.findatex.validator.batch.BatchSummary;
import com.findatex.validator.batch.BatchValidationOptions;
import com.findatex.validator.batch.BatchValidationService;
import com.findatex.validator.config.AppSettings;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.template.tpt.TptTemplate;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CombinedXlsxReportWriterTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();
    private static final Set<ProfileKey> PROFILES = Set.of(
            TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB,
            TptProfiles.NW_675, TptProfiles.SST);

    @Test
    void writesAllFiveSheetsWhenFailuresPresent(@TempDir Path tmp) throws Exception {
        BatchSummary summary = buildSummary(tmp, /* includeBroken */ true);
        Path out = tmp.resolve("combined.report.xlsx");

        new CombinedXlsxReportWriter(CATALOG, new TptTemplate().profilesFor(TptTemplate.V7_0),
                TptTemplate.V7_0, GenerationUi.DESKTOP).write(summary, out);

        assertThat(Files.exists(out)).isTrue();
        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            List<String> sheetNames = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                sheetNames.add(wb.getSheetAt(i).getSheetName());
            }
            assertThat(sheetNames).containsExactly(
                    "Batch Summary",
                    "All Findings",
                    "Aggregate Field Coverage",
                    "Per-File Scores",
                    "Skipped or Failed Files");
        }
    }

    @Test
    void omitsSkippedSheetWhenNoFailures(@TempDir Path tmp) throws Exception {
        BatchSummary summary = buildSummary(tmp, /* includeBroken */ false);
        Path out = tmp.resolve("combined.report.xlsx");

        new CombinedXlsxReportWriter(CATALOG, new TptTemplate().profilesFor(TptTemplate.V7_0),
                TptTemplate.V7_0, GenerationUi.DESKTOP).write(summary, out);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            List<String> sheetNames = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                sheetNames.add(wb.getSheetAt(i).getSheetName());
            }
            assertThat(sheetNames).containsExactly(
                    "Batch Summary",
                    "All Findings",
                    "Aggregate Field Coverage",
                    "Per-File Scores");
        }
    }

    @Test
    void allFindingsRowCountMatchesSumPerFile(@TempDir Path tmp) throws Exception {
        BatchSummary summary = buildSummary(tmp, false);
        long expectedFindings = summary.results().stream()
                .filter(r -> r.status() == BatchFileStatus.OK)
                .mapToLong(r -> r.findings().size())
                .sum();

        Path out = tmp.resolve("combined.report.xlsx");
        new CombinedXlsxReportWriter(CATALOG, new TptTemplate().profilesFor(TptTemplate.V7_0),
                TptTemplate.V7_0, GenerationUi.DESKTOP).write(summary, out);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet allFindings = wb.getSheet("All Findings");
            assertThat(allFindings.getLastRowNum()).isEqualTo((int) expectedFindings);  // 0-based; header on row 0
        }
    }

    @Test
    void perFileScoresContainsRowPerFilePerCategory(@TempDir Path tmp) throws Exception {
        BatchSummary summary = buildSummary(tmp, false);
        long expectedRows = summary.results().stream()
                .filter(r -> r.status() == BatchFileStatus.OK)
                .mapToLong(r -> r.report().scores().size())
                .sum();

        Path out = tmp.resolve("combined.report.xlsx");
        new CombinedXlsxReportWriter(CATALOG, new TptTemplate().profilesFor(TptTemplate.V7_0),
                TptTemplate.V7_0, GenerationUi.DESKTOP).write(summary, out);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet perFile = wb.getSheet("Per-File Scores");
            assertThat(perFile.getLastRowNum()).isEqualTo((int) expectedRows);
        }
    }

    private BatchSummary buildSummary(Path tmp, boolean includeBroken) throws Exception {
        Path folder = Files.createDirectory(tmp.resolve("batch_input_" + (includeBroken ? "with_broken" : "ok")));
        copySample("/sample/clean_v7.xlsx", folder.resolve("clean.xlsx"));
        copySample("/sample/bad_formats.xlsx", folder.resolve("bad.xlsx"));
        List<Path> files = new ArrayList<>();
        files.add(folder.resolve("clean.xlsx"));
        files.add(folder.resolve("bad.xlsx"));
        if (includeBroken) {
            Path broken = folder.resolve("broken.xlsx");
            Files.writeString(broken, "definitely-not-xlsx");
            files.add(broken);
        }

        BatchValidationOptions opts = new BatchValidationOptions(
                new TptTemplate(),
                TptTemplate.V7_0,
                PROFILES,
                false,
                AppSettings.defaults(),
                null);
        return new BatchValidationService(CATALOG, opts).run(files, () -> false);
    }

    private static void copySample(String resource, Path target) throws Exception {
        URL url = CombinedXlsxReportWriterTest.class.getResource(resource);
        assertThat(url).as("missing test resource %s", resource).isNotNull();
        Files.copy(Path.of(url.toURI()), target, StandardCopyOption.REPLACE_EXISTING);
    }
}
