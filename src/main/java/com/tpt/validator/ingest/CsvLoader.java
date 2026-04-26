package com.tpt.validator.ingest;

import com.tpt.validator.domain.RawCell;
import com.tpt.validator.domain.TptFile;
import com.tpt.validator.domain.TptRow;
import com.tpt.validator.spec.SpecCatalog;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
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
        char delimiter = detectDelimiter(file);
        log.debug("CSV delimiter for {}: {}", file.getFileName(), (int) delimiter);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setQuote('"')
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(r)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                return new TptFile(file, "csv", List.of(), Map.of(), List.of(), List.of());
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
                    file.getFileName(), rows.size(), map.size(), unmapped.size());
            return new TptFile(file, "csv", headers, map, unmapped, rows);
        }
    }

    static char detectDelimiter(Path file) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line = br.readLine();
            if (line == null) return ',';
            int semi = countOutsideQuotes(line, ';');
            int comma = countOutsideQuotes(line, ',');
            int tab = countOutsideQuotes(line, '\t');
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
