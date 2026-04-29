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
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
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

class CombinedXlsxReportWriterAnnotatedSourceTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();
    private static final Set<ProfileKey> PROFILES = Set.of(
            TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB,
            TptProfiles.NW_675, TptProfiles.SST);

    @Test
    void appendsOnePerFileSheetPerOkFile(@TempDir Path tmp) throws Exception {
        BatchSummary summary = buildTwoOkAndOneBrokenSummary(tmp);
        Path out = tmp.resolve("combined.report.xlsx");

        new CombinedXlsxReportWriter(CATALOG, new TptTemplate().profilesFor(TptTemplate.V7_0),
                TptTemplate.V7_0, GenerationUi.DESKTOP).write(summary, out, true);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            List<String> sheetNames = sheetNames(wb);
            assertThat(sheetNames).startsWith(
                    "Batch Summary",
                    "All Findings",
                    "Aggregate Field Coverage",
                    "Per-File Scores",
                    "Skipped or Failed Files");
            // Two OK files → two annotated-source sheets, broken file → no extra sheet.
            assertThat(sheetNames).hasSize(7);
            assertThat(sheetNames).contains("clean.xlsx", "bad.xlsx");
        }
    }

    @Test
    void omitsPerFileSheetsWhenNotRequested(@TempDir Path tmp) throws Exception {
        BatchSummary summary = buildTwoOkAndOneBrokenSummary(tmp);
        Path out = tmp.resolve("combined.report.xlsx");

        new CombinedXlsxReportWriter(CATALOG, new TptTemplate().profilesFor(TptTemplate.V7_0),
                TptTemplate.V7_0, GenerationUi.DESKTOP).write(summary, out, false);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            assertThat(sheetNames(wb)).containsExactly(
                    "Batch Summary",
                    "All Findings",
                    "Aggregate Field Coverage",
                    "Per-File Scores",
                    "Skipped or Failed Files");
        }
    }

    @Test
    void disambiguatesCollidingFileBasenames(@TempDir Path tmp) throws Exception {
        // Two files with identical basename in different sub-folders.
        Path a = Files.createDirectory(tmp.resolve("sub_a"));
        Path b = Files.createDirectory(tmp.resolve("sub_b"));
        copySample("/sample/clean_v7.xlsx", a.resolve("clean.xlsx"));
        copySample("/sample/clean_v7.xlsx", b.resolve("clean.xlsx"));
        List<Path> files = List.of(a.resolve("clean.xlsx"), b.resolve("clean.xlsx"));

        BatchSummary summary = runBatch(files);
        Path out = tmp.resolve("combined.report.xlsx");
        new CombinedXlsxReportWriter(CATALOG, new TptTemplate().profilesFor(TptTemplate.V7_0),
                TptTemplate.V7_0, GenerationUi.DESKTOP).write(summary, out, true);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            List<String> names = sheetNames(wb);
            assertThat(names).contains("clean.xlsx", "clean.xlsx ~2");
            assertThat(names.stream().filter(n -> n.startsWith("clean")).count()).isEqualTo(2);
        }
    }

    @Test
    void batchSummaryFileCellLinksToAnnotatedSourceSheet(@TempDir Path tmp) throws Exception {
        BatchSummary summary = buildTwoOkAndOneBrokenSummary(tmp);
        Path out = tmp.resolve("combined.report.xlsx");
        new CombinedXlsxReportWriter(CATALOG, new TptTemplate().profilesFor(TptTemplate.V7_0),
                TptTemplate.V7_0, GenerationUi.DESKTOP).write(summary, out, true);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet summarySheet = wb.getSheet("Batch Summary");
            // Locate the per-file table header row "File"; the rows below carry hyperlinks.
            int fileHeaderRow = -1;
            for (int r = 0; r <= summarySheet.getLastRowNum(); r++) {
                Row rr = summarySheet.getRow(r);
                if (rr == null) continue;
                Cell c0 = rr.getCell(0);
                if (c0 != null && "File".equals(c0.getStringCellValue())) {
                    fileHeaderRow = r;
                    break;
                }
            }
            assertThat(fileHeaderRow).as("File header row").isGreaterThan(0);

            // First data row after the header carries the link for the first OK file.
            Cell firstFile = summarySheet.getRow(fileHeaderRow + 1).getCell(0);
            Hyperlink link = firstFile.getHyperlink();
            assertThat(link).as("hyperlink on first file row").isNotNull();
            assertThat(link.getType()).isEqualTo(HyperlinkType.DOCUMENT);
            assertThat(link.getAddress()).contains("'" + firstFile.getStringCellValue() + "'!A1");
        }
    }

    private BatchSummary buildTwoOkAndOneBrokenSummary(Path tmp) throws Exception {
        Path folder = Files.createDirectory(tmp.resolve("batch_input"));
        copySample("/sample/clean_v7.xlsx", folder.resolve("clean.xlsx"));
        copySample("/sample/bad_formats.xlsx", folder.resolve("bad.xlsx"));
        Path broken = folder.resolve("broken.xlsx");
        Files.writeString(broken, "definitely-not-xlsx");
        List<Path> files = new ArrayList<>(List.of(
                folder.resolve("clean.xlsx"),
                folder.resolve("bad.xlsx"),
                broken));
        return runBatch(files);
    }

    private BatchSummary runBatch(List<Path> files) {
        BatchValidationOptions opts = new BatchValidationOptions(
                new TptTemplate(),
                TptTemplate.V7_0,
                PROFILES,
                false,
                AppSettings.defaults(),
                null);
        BatchSummary summary = new BatchValidationService(CATALOG, opts).run(files, () -> false);
        // Sanity: at least one OK file present (so per-file sheets are non-empty).
        assertThat(summary.results().stream().filter(r -> r.status() == BatchFileStatus.OK).count())
                .isGreaterThan(0);
        return summary;
    }

    private static List<String> sheetNames(Workbook wb) {
        List<String> sheetNames = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            sheetNames.add(wb.getSheetAt(i).getSheetName());
        }
        return sheetNames;
    }

    private static void copySample(String resource, Path target) throws Exception {
        URL url = CombinedXlsxReportWriterAnnotatedSourceTest.class.getResource(resource);
        assertThat(url).as("missing test resource %s", resource).isNotNull();
        Files.copy(Path.of(url.toURI()), target, StandardCopyOption.REPLACE_EXISTING);
    }
}
