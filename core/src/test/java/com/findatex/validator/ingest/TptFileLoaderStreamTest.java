package com.findatex.validator.ingest;

import com.findatex.validator.domain.RawCell;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TptFileLoaderStreamTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    @Test
    void streamAndPathProduceEquivalentResultForXlsxSample() throws IOException {
        Path sample = Path.of("src/test/resources/sample/clean_v7.xlsx");
        TptFile fromPath = new TptFileLoader(CATALOG).load(sample);

        byte[] bytes = Files.readAllBytes(sample);
        TptFile fromStream;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            fromStream = new TptFileLoader(CATALOG).load(in, "clean_v7.xlsx");
        }

        assertEquivalent(fromPath, fromStream, "clean_v7.xlsx");
    }

    @Test
    void streamAndPathProduceEquivalentResultForCsvSample() throws IOException {
        Path sample = Path.of("src/test/resources/sample/missing_mandatory.csv");
        TptFile fromPath = new TptFileLoader(CATALOG).load(sample);

        byte[] bytes = Files.readAllBytes(sample);
        TptFile fromStream;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            fromStream = new TptFileLoader(CATALOG).load(in, "missing_mandatory.csv");
        }

        assertEquivalent(fromPath, fromStream, "missing_mandatory.csv");
    }

    @Test
    void utf16CsvLoadsLikeTheUtf8Original() throws IOException {
        // Excel's "Unicode Text" export: UTF-16LE with BOM. Production saw such uploads
        // rejected as binary; they must parse to the same rows as the UTF-8 file.
        Path sample = Path.of("src/test/resources/sample/missing_mandatory.csv");
        TptFile utf8 = new TptFileLoader(CATALOG).load(sample);
        String text = Files.readString(sample);
        byte[][] variants = {
                concat(new byte[]{(byte) 0xFF, (byte) 0xFE}, text.getBytes(java.nio.charset.StandardCharsets.UTF_16LE)),
                text.getBytes(java.nio.charset.StandardCharsets.UTF_16LE),          // no BOM
                text.getBytes(java.nio.charset.StandardCharsets.UTF_16),            // BE with BOM
        };
        for (byte[] v : variants) {
            TptFile loaded;
            try (ByteArrayInputStream in = new ByteArrayInputStream(v)) {
                loaded = new TptFileLoader(CATALOG).load(in, "missing_mandatory.csv");
            }
            assertEquivalent(utf8, loaded, "missing_mandatory.csv");
        }
    }

    @Test
    void utf8CsvIsNotMistakenForUtf16() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/sample/missing_mandatory.csv"));
        assertThat(CsvLoader.utf16Charset(bytes)).isNull();
        assertThat(CsvLoader.toUtf8(bytes)).isSameAs(bytes);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    @Test
    void streamLoaderRejectsUnknownExtension() {
        byte[] payload = "garbage".getBytes();
        assertThatThrownBy(() ->
                new TptFileLoader(CATALOG).load(new ByteArrayInputStream(payload), "foo.pdf"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Unsupported file extension");
    }

    @Test
    void streamLoaderUsesFilenameAsSyntheticSource() throws IOException {
        Path sample = Path.of("src/test/resources/sample/clean_v7.xlsx");
        byte[] bytes = Files.readAllBytes(sample);
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            TptFile fromStream = new TptFileLoader(CATALOG).load(in, "fund_xyz.xlsx");
            assertThat(fromStream.source().getFileName().toString()).isEqualTo("fund_xyz.xlsx");
        }
    }

    private static void assertEquivalent(TptFile a, TptFile b, String expectedSyntheticName) {
        assertThat(b.source().getFileName().toString()).isEqualTo(expectedSyntheticName);
        assertThat(b.inputFormat()).isEqualTo(a.inputFormat());
        assertThat(b.rawHeaders()).isEqualTo(a.rawHeaders());
        assertThat(b.unmappedHeaders()).isEqualTo(a.unmappedHeaders());

        Map<Integer, String> ma = a.headerToNumKey();
        Map<Integer, String> mb = b.headerToNumKey();
        assertThat(mb).isEqualTo(ma);

        List<TptRow> rowsA = a.rows();
        List<TptRow> rowsB = b.rows();
        assertThat(rowsB).hasSameSizeAs(rowsA);
        for (int i = 0; i < rowsA.size(); i++) {
            TptRow ra = rowsA.get(i);
            TptRow rb = rowsB.get(i);
            assertThat(rb.rowIndex()).isEqualTo(ra.rowIndex());
            Map<String, RawCell> cellsA = ra.all();
            Map<String, RawCell> cellsB = rb.all();
            assertThat(cellsB.keySet()).isEqualTo(cellsA.keySet());
            for (String key : cellsA.keySet()) {
                RawCell ca = cellsA.get(key);
                RawCell cb = cellsB.get(key);
                assertThat(cb.value()).as("row %d field %s value", i, key).isEqualTo(ca.value());
                assertThat(cb.sourceRow()).as("row %d field %s sourceRow", i, key).isEqualTo(ca.sourceRow());
                assertThat(cb.sourceCol()).as("row %d field %s sourceCol", i, key).isEqualTo(ca.sourceCol());
            }
        }
    }
}
