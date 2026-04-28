package com.findatex.validator.ingest;

import com.findatex.validator.domain.RawCell;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.SpecCatalog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test: POI 5.3.0+ disables XXE by default. This test builds a
 * minimal-viable XLSX whose {@code sheet1.xml} declares an external entity
 * pointing at {@code /etc/passwd}, then loads it through {@link XlsxLoader}.
 * If POI ever re-enables external entity resolution by accident, the cell
 * value will contain the contents of {@code /etc/passwd} (or the build
 * server's equivalent), making this test fail loudly.
 */
class XlsxXxeSafetyTest {

    @Test
    void poiDoesNotResolveExternalEntitiesInWorksheetXml() throws Exception {
        Path malicious = buildXlsxWithXxeInSheet();
        try {
            // We don't have a real spec catalog handy and TptFileLoader expects one,
            // but XlsxLoader needs only a no-op catalog for a header-less synthetic XLSX.
            SpecCatalog catalog = new SpecCatalog(List.<FieldSpec>of());
            XlsxLoader loader = new XlsxLoader(catalog);
            TptFile loaded;
            try {
                loaded = loader.load(malicious);
            } catch (IOException | RuntimeException e) {
                // Hard rejection of the entity is also acceptable — verify the failure
                // message itself doesn't expose /etc/passwd contents.
                String msg = e.getMessage() == null ? "" : e.getMessage();
                assertThat(msg).doesNotContain("root:").doesNotContain("/bin/bash");
                return;
            }
            // Soft path: POI parsed the file but stripped the entity.
            for (TptRow row : loaded.rows()) {
                for (RawCell cell : row.all().values()) {
                    String value = cell.value();
                    if (value == null) continue;
                    assertThat(value)
                            .as("cell value must not contain /etc/passwd content")
                            .doesNotContain("root:")
                            .doesNotContain("/bin/bash");
                }
            }
        } finally {
            Files.deleteIfExists(malicious);
        }
    }

    /** Builds a minimal-viable XLSX whose worksheet XML attempts an XXE expansion. */
    private static Path buildXlsxWithXxeInSheet() throws IOException {
        Path tmp = Files.createTempFile("xxe-", ".xlsx");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
            put(zos, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """);
            put(zos, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """);
            put(zos, "xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
                """);
            put(zos, "xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
                """);
            put(zos, "xl/worksheets/sheet1.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <sheetData>
                        <row r="1"><c r="A1" t="inlineStr"><is><t>&xxe;</t></is></c></row>
                    </sheetData>
                </worksheet>
                """);
        }
        return tmp;
    }

    private static void put(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry e = new ZipEntry(name);
        zos.putNextEntry(e);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
