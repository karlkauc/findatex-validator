package com.findatex.validator.report;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.template.eet.EetProfiles;
import com.findatex.validator.template.eet.EetTemplate;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.ValidationEngine;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-tests that the Excel report renders the EET template's profile dimension
 * (8 SFDR / MiFID / IDD / Look-through columns) instead of TPT's. Disabled if
 * the EET sample fixtures haven't been generated.
 */
@EnabledIf("samplesPresent")
class XlsxReportWriterEetTest {

    private static final EetTemplate TEMPLATE = new EetTemplate();
    private static final SpecCatalog CATALOG =
            TEMPLATE.specLoaderFor(EetTemplate.V1_1_3).load();
    private static final ProfileSet PROFILES = EetProfiles.ALL;
    private static final ValidationEngine ENGINE =
            new ValidationEngine(CATALOG, TEMPLATE.ruleSetFor(EetTemplate.V1_1_3));

    static boolean samplesPresent() {
        return Files.isDirectory(samplesDir());
    }

    /**
     * Resolve {@code samples/eet/} from the JVM's CWD. When Surefire launches
     * the test JVM with {@code core/} as CWD (the multi-module default) we walk
     * up to find the project root that actually owns {@code samples/}.
     */
    private static Path samplesDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path direct = cwd.resolve("samples").resolve("eet");
        if (Files.isDirectory(direct)) return direct;
        Path parent = cwd.getParent();
        if (parent != null) {
            Path up = parent.resolve("samples").resolve("eet");
            if (Files.isDirectory(up)) return up;
        }
        return direct;
    }

    @Test
    void fieldCoverageHeaderListsAllEetProfiles(@TempDir Path tmp) throws Exception {
        Path samplePath = samplesDir().resolve("01_clean.xlsx");
        TptFile file = new TptFileLoader(CATALOG).load(samplePath);
        Set<ProfileKey> active = Set.of(EetProfiles.SFDR_PERIODIC);
        List<Finding> findings = ENGINE.validate(file, active);
        QualityReport report = new QualityScorer(CATALOG).score(file, active, findings);

        Path out = tmp.resolve("eet-report.xlsx");
        new XlsxReportWriter(CATALOG, PROFILES, EetTemplate.V1_1_3, GenerationUi.WEB).write(report, out);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet coverage = wb.getSheet("Field Coverage");
            Row hdr = coverage.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < hdr.getLastCellNum(); c++) headers.add(hdr.getCell(c).getStringCellValue());

            // Every profile in EetProfiles.ALL must appear in the header.
            for (ProfileKey p : PROFILES.all()) {
                assertThat(headers)
                        .as("EET coverage header must include profile %s", p.code())
                        .contains(p.displayName());
            }
            // None of the TPT profile names should leak through.
            assertThat(headers).doesNotContain("Solvency II", "NW 675", "SST (FINMA)");
        }
    }

    @Test
    void versionFieldShowsMandatoryFlagInSfdrPeriodicColumn(@TempDir Path tmp) throws Exception {
        Path samplePath = samplesDir().resolve("01_clean.xlsx");
        TptFile file = new TptFileLoader(CATALOG).load(samplePath);
        Set<ProfileKey> active = Set.of(EetProfiles.SFDR_PERIODIC);
        List<Finding> findings = ENGINE.validate(file, active);
        QualityReport report = new QualityScorer(CATALOG).score(file, active, findings);

        Path out = tmp.resolve("eet-report.xlsx");
        new XlsxReportWriter(CATALOG, PROFILES, EetTemplate.V1_1_3, GenerationUi.WEB).write(report, out);

        try (InputStream in = Files.newInputStream(out);
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet coverage = wb.getSheet("Field Coverage");
            Row hdr = coverage.getRow(0);
            int sfdrPeriodicCol = -1;
            for (int c = 0; c < hdr.getLastCellNum(); c++) {
                if (EetProfiles.SFDR_PERIODIC.displayName().equals(hdr.getCell(c).getStringCellValue())) {
                    sfdrPeriodicCol = c;
                    break;
                }
            }
            assertThat(sfdrPeriodicCol).as("SFDR Periodic column must exist").isGreaterThanOrEqualTo(0);

            // EET NUM=1 (path 00010_EET_Version) is M-flagged for SFDR_PERIODIC. Find its row
            // and check the SFDR Periodic cell reads "M".
            Row versionRow = null;
            for (int r = 1; r <= coverage.getLastRowNum(); r++) {
                Row rr = coverage.getRow(r);
                Cell numCell = rr.getCell(0);
                if (numCell != null && "1".equals(numCell.getStringCellValue())) {
                    versionRow = rr;
                    break;
                }
            }
            assertThat(versionRow).as("EET NUM=1 row must be present in coverage sheet").isNotNull();
            assertThat(versionRow.getCell(sfdrPeriodicCol).getStringCellValue())
                    .as("EET version field is M-flagged for SFDR Periodic")
                    .isEqualTo("M");
        }
    }
}
