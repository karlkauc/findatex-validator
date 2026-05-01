package com.findatex.validator.ingest;

import com.findatex.validator.domain.RawCell;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.SpecCatalog;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CsvLoader {

    private static final Logger log = LoggerFactory.getLogger(CsvLoader.class);

    private final SpecCatalog catalog;

    public CsvLoader(SpecCatalog catalog) {
        this.catalog = catalog;
    }

    public TptFile load(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return load(bytes, file, null);
    }

    public TptFile load(InputStream in, String filename) throws IOException {
        byte[] bytes = in.readAllBytes();
        // Web upload path: keep the bytes on the TptFile so the report writer can rebuild
        // the Annotated-Source tab without going back to the (now-deleted) tempfile.
        return load(bytes, Path.of(filename == null || filename.isBlank() ? "uploaded.csv" : filename), bytes);
    }

    private TptFile load(byte[] bytes, Path source, byte[] sourceBytes) throws IOException {
        bytes = stripUtf8Bom(bytes);
        char delimiter = detectDelimiter(bytes);
        log.debug("CSV delimiter for {}: {}", source.getFileName(), (int) delimiter);
        // FinDatEx pipe-delimited files don't quote their values (the whole point
        // of '|' is that it never appears inside data). Some producers still wrap
        // descriptive prose in "..." but fail to escape inner '"' characters, which
        // makes Apache Commons CSV abort with "Invalid character between encapsulated
        // token and delimiter". Treating '"' as a literal there matches the convention.
        CSVFormat.Builder b = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(true)
                .setTrim(true);
        if (delimiter != '|') b.setQuote('"');
        else b.setQuote(null);
        CSVFormat format = b.build();

        try (Reader r = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(r)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                return new TptFile(source, "csv", List.of(), Map.of(), List.of(), List.of(), sourceBytes);
            }
            List<String> headers = new ArrayList<>();
            for (String h : records.get(0)) headers.add(h);
            List<String> unmapped = new ArrayList<>();
            Map<Integer, String> map = new HeaderMapper(catalog).map(headers, unmapped);

            List<TptRow> rows = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                CSVRecord rec = records.get(i);
                TptRow row = new TptRow(i);
                for (Map.Entry<Integer, String> e : map.entrySet()) {
                    int col = e.getKey();
                    if (col < rec.size()) {
                        row.put(e.getValue(), new RawCell(rec.get(col), i + 1, col + 1));
                    }
                }
                rows.add(row);
            }
            log.info("Loaded CSV {} ({} rows, {} mapped fields, {} unmapped headers)",
                    source.getFileName(), rows.size(), map.size(), unmapped.size());
            return new TptFile(source, "csv", headers, map, unmapped, rows, sourceBytes);
        }
    }

    /** Drop a leading UTF-8 BOM ({@code EF BB BF}) so the first header parses cleanly.
     *  Several producers (DWS, Amundi) emit BOM-prefixed CSVs; without this strip the first
     *  header would carry a {@code ﻿} prefix and never match the spec, leading to
     *  spurious "field N missing" findings on every row. */
    private static byte[] stripUtf8Bom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            byte[] out = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, out, 0, out.length);
            return out;
        }
        return bytes;
    }

    static char detectDelimiter(Path file) throws IOException {
        return detectDelimiter(Files.readAllBytes(file));
    }

    static char detectDelimiter(byte[] bytes) throws IOException {
        try (BufferedReader br = new BufferedReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            String line = br.readLine();
            if (line == null) return ',';
            int pipe = countOutsideQuotes(line, '|');
            int semi = countOutsideQuotes(line, ';');
            int comma = countOutsideQuotes(line, ',');
            int tab = countOutsideQuotes(line, '\t');
            if (pipe >= semi && pipe >= comma && pipe >= tab && pipe > 0) return '|';
            if (semi >= comma && semi >= tab) return ';';
            if (tab >= comma) return '\t';
            return ',';
        }
    }

    private static int countOutsideQuotes(String s, char target) {
        int count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            else if (!inQuotes && c == target) count++;
        }
        return count;
    }
}
