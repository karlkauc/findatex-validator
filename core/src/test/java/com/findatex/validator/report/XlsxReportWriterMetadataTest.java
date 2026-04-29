package com.findatex.validator.report;

import com.findatex.validator.AppInfo;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.template.tpt.TptRuleSet;
import com.findatex.validator.template.tpt.TptTemplate;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.ValidationEngine;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class XlsxReportWriterMetadataTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    @Test
    void setsCorePropertiesCreatorAndTitle(@TempDir Path tmp) throws Exception {
        Path out = writeReport(tmp, "/sample/clean_v7.xlsx", TptTemplate.V7_0, GenerationUi.DESKTOP);
        try (InputStream in = Files.newInputStream(out);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            POIXMLProperties.CoreProperties core = wb.getProperties().getCoreProperties();
            assertThat(core.getCreator()).contains("FinDatEx Validator");
            assertThat(core.getTitle()).contains("FinDatEx Validator").contains("TPT V7");
            assertThat(core.getKeywords()).contains("FinDatEx").contains("TPT");
            assertThat(core.getCategory()).isEqualTo("Validation Report");
            assertThat(core.getDescription()).contains("Quality report");
        }
    }

    @Test
    void setsExtendedPropertiesApplication(@TempDir Path tmp) throws Exception {
        Path out = writeReport(tmp, "/sample/clean_v7.xlsx", TptTemplate.V7_0, GenerationUi.WEB);
        try (InputStream in = Files.newInputStream(out);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            POIXMLProperties.ExtendedProperties ext = wb.getProperties().getExtendedProperties();
            assertThat(ext.getApplication()).isEqualTo("FinDatEx Validator");
            assertThat(ext.getAppVersion()).isEqualTo(AppInfo.version());
        }
    }

    @Test
    void setsCustomPropertiesTemplateAndFindings(@TempDir Path tmp) throws Exception {
        QualityReport report = buildReport("/sample/bad_formats.xlsx");
        Path out = tmp.resolve("custom.xlsx");
        new XlsxReportWriter(CATALOG, TptProfiles.ALL, TptTemplate.V7_0, GenerationUi.WEB).write(report, out);

        try (InputStream in = Files.newInputStream(out);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Map<String, Object> map = customPropertyMap(wb);
            assertThat(map).containsKeys(
                    "FinDatEx-Validator-Version",
                    "FinDatEx-Validator-Build-Timestamp",
                    "FinDatEx-Report-Schema-Version",
                    "Template-Id",
                    "Template-Version",
                    "Template-Label",
                    "Source-Filename",
                    "Source-Format",
                    "Source-Row-Count",
                    "Active-Profiles",
                    "Findings-Total",
                    "Findings-Errors",
                    "Findings-Warnings",
                    "Findings-Info",
                    "Quality-Score-Total",
                    "Generated-At-UTC",
                    "Generation-UI");
            assertThat(map.get("Template-Id")).isEqualTo("TPT");
            assertThat(map.get("Template-Label")).isEqualTo(TptTemplate.V7_0.label());
            assertThat(map.get("Template-Version")).isEqualTo(TptTemplate.V7_0.version());
            assertThat(map.get("Source-Format")).isEqualTo("xlsx");
            assertThat(map.get("Generation-UI")).isEqualTo("Web");
            assertThat(map.get("Findings-Total")).isEqualTo(report.findings().size());
        }
    }

    @Test
    void summarySheetContainsProducedByBanner(@TempDir Path tmp) throws Exception {
        Path out = writeReport(tmp, "/sample/clean_v7.xlsx", TptTemplate.V7_0, GenerationUi.DESKTOP);
        try (InputStream in = Files.newInputStream(out);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet summary = wb.getSheet("Summary");
            String dump = dumpStrings(summary);
            assertThat(dump).contains("Produced by FinDatEx Validator");
            assertThat(dump).contains("Report metadata");
            assertThat(dump).contains("https://github.com/karlkauc/findatex-validator");
        }
    }

    @Test
    void summarySheetTitleReflectsTemplate(@TempDir Path tmp) throws Exception {
        Path out = writeReport(tmp, "/sample/clean_v7.xlsx", TptTemplate.V7_0, GenerationUi.DESKTOP);
        try (InputStream in = Files.newInputStream(out);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet summary = wb.getSheet("Summary");
            String firstRow = summary.getRow(0).getCell(0).getStringCellValue();
            assertThat(firstRow).isEqualTo(TptTemplate.V7_0.label() + " Quality Report");
            assertThat(firstRow).startsWith("TPT V7");
        }
    }

    @Test
    void coreTitleContainsTemplateLabel(@TempDir Path tmp) throws Exception {
        Path out = writeReport(tmp, "/sample/clean_v7.xlsx", TptTemplate.V7_0, GenerationUi.DESKTOP);
        try (InputStream in = Files.newInputStream(out);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            String title = wb.getProperties().getCoreProperties().getTitle();
            assertThat(title).contains(TptTemplate.V7_0.label());
        }
    }

    private static Map<String, Object> customPropertyMap(XSSFWorkbook wb) {
        Map<String, Object> m = new HashMap<>();
        var underlying = wb.getProperties().getCustomProperties().getUnderlyingProperties();
        for (var prop : underlying.getPropertyList()) {
            String name = prop.getName();
            Object value;
            if (prop.isSetLpwstr()) value = prop.getLpwstr();
            else if (prop.isSetLpstr()) value = prop.getLpstr();
            else if (prop.isSetI4()) value = prop.getI4();
            else if (prop.isSetI8()) value = prop.getI8();
            else if (prop.isSetR8()) value = prop.getR8();
            else if (prop.isSetBool()) value = prop.getBool();
            else value = prop.toString();
            m.put(name, value);
        }
        return m;
    }

    private static String dumpStrings(Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) continue;
                if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                    sb.append(cell.getStringCellValue()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private Path writeReport(Path tmp,
                             String resource,
                             com.findatex.validator.template.api.TemplateVersion version,
                             GenerationUi ui) throws Exception {
        QualityReport report = buildReport(resource);
        Path out = tmp.resolve("report.xlsx");
        new XlsxReportWriter(CATALOG, TptProfiles.ALL, version, ui).write(report, out);
        return out;
    }

    private QualityReport buildReport(String resourcePath) throws Exception {
        URL url = XlsxReportWriterMetadataTest.class.getResource(resourcePath);
        assertThat(url).as("missing test resource %s", resourcePath).isNotNull();
        Path p = Path.of(url.toURI());
        TptFile file = new TptFileLoader(CATALOG).load(p);
        Set<ProfileKey> profiles = Set.of(TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB,
                TptProfiles.NW_675, TptProfiles.SST);
        List<Finding> findings = new ValidationEngine(CATALOG, new TptRuleSet()).validate(file, profiles);
        return new QualityScorer(CATALOG).score(file, profiles, findings);
    }
}
