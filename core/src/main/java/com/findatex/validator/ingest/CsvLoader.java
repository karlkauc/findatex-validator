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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

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
        Layout layout = detectLayout(bytes, this::recognisedHeaders);
        log.debug("CSV layout for {}: delimiter {}, header record {}",
                source.getFileName(), (int) layout.delimiter(), layout.headerRecord());
        List<List<String>> records = records(bytes, layout.delimiter());
        int headerRecord = layout.headerRecord();
        if (records.size() <= headerRecord) {
            return new TptFile(source, "csv", List.of(), Map.of(), List.of(), List.of(), sourceBytes);
        }
        List<String> headers = records.get(headerRecord);
        List<String> unmapped = new ArrayList<>();
        Map<Integer, String> map = new HeaderMapper(catalog).map(headers, unmapped);

        List<TptRow> rows = new ArrayList<>();
        for (int i = headerRecord + 1; i < records.size(); i++) {
            List<String> rec = records.get(i);
            TptRow row = new TptRow(i - headerRecord);
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

    /**
     * Header score of a record: its recognised column names. Bare numbers don't count — they
     * are valid headers, but a data row is full of numbers that happen to be field numbers —
     * and neither does a record whose names are mostly unknown.
     */
    private int recognisedHeaders(List<String> record) {
        int named = 0;
        int filled = 0;
        for (String cell : record) {
            if (cell.isBlank()) continue;
            filled++;
            if (cell.chars().anyMatch(Character::isLetter) && catalog.matchHeader(cell).isPresent()) named++;
        }
        return named == filled || (named >= 3 && named * 2 >= filled) ? named : 0;
    }

    /**
     * Where the header sits and how the cells are separated.
     *
     * @param headerRecord 0-based index into {@link #records} — blank lines are not records
     */
    public record Layout(char delimiter, int headerRecord) {
    }

    /**
     * Picks delimiter and header record together: among the first {@value #HEADER_SCAN_RECORDS}
     * records, read with each candidate delimiter, the one {@code headerScore} rates highest
     * wins. That finds the header behind a title line, leading blank lines or Excel's
     * {@code sep=;} hint — the CSV counterpart of {@code XlsxLoader.findHeaderRow}. A delimiter
     * other than the first line's dominant one needs a score of at least
     * {@value #MIN_SCORE_TO_SWITCH_DELIMITER}. When no record scores at all, the dominant
     * separator of the first line decides and the header is the first record (the second
     * after a {@code sep=} line).
     */
    public static Layout detectLayout(byte[] bytes, ToIntFunction<List<String>> headerScore) {
        String head = head(stripUtf8Bom(bytes));
        String firstLine = head.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        boolean sepHint = SEP_HINT.matcher(firstLine).matches();
        char preferred;
        if (sepHint) {
            preferred = firstLine.trim().charAt(4);
        } else {
            preferred = dominantDelimiter(firstLine);
        }

        Layout best = new Layout(preferred, sepHint ? 1 : 0);
        int bestScore = 0;
        for (char delimiter : candidates(preferred)) {
            List<List<String>> records = parse(new StringReader(head), delimiter, true);
            for (int i = 0; i < records.size(); i++) {
                int score = headerScore.applyAsInt(records.get(i));
                // Read with the wrong delimiter a header line is one cell that may still
                // resolve through its leading field number — not enough to switch delimiter.
                if (delimiter != preferred && score < MIN_SCORE_TO_SWITCH_DELIMITER) continue;
                if (score > bestScore) {
                    bestScore = score;
                    best = new Layout(delimiter, i);
                }
            }
        }
        return best;
    }

    /**
     * Every non-blank line of the file as trimmed cells, in file order. Shared with the
     * annotated-source mirror so its grid lines up with the {@link RawCell} coordinates.
     */
    public static List<List<String>> records(byte[] bytes, char delimiter) throws IOException {
        bytes = stripUtf8Bom(bytes);
        try {
            return parse(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8),
                    delimiter, false);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static final int HEADER_SCAN_RECORDS = 50;
    private static final int MIN_SCORE_TO_SWITCH_DELIMITER = 3;
    private static final char[] DELIMITERS = {'|', ';', '\t', ','};
    private static final Pattern SEP_HINT = Pattern.compile("\\s*sep=[|;\\t,]\\s*", Pattern.CASE_INSENSITIVE);

    private static char[] candidates(char preferred) {
        char[] out = new char[DELIMITERS.length];
        out[0] = preferred;
        int n = 1;
        for (char d : DELIMITERS) {
            if (d != preferred) out[n++] = d;
        }
        return out;
    }

    /**
     * @param lenient keep what was read when the input ends inside a quoted cell — the head
     *                used for layout detection may be cut off mid-record
     */
    private static List<List<String>> parse(Reader in, char delimiter, boolean lenient) {
        // FinDatEx pipe-delimited files don't quote their values (the whole point
        // of '|' is that it never appears inside data). Some producers still wrap
        // descriptive prose in "..." but fail to escape inner '"' characters, which
        // makes Apache Commons CSV abort with "Invalid character between encapsulated
        // token and delimiter". Treating '"' as a literal there matches the convention;
        // a pair of quotes around the whole cell is dropped afterwards.
        boolean literalQuotes = delimiter == '|';
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setQuote(literalQuotes ? null : Character.valueOf('"'))
                .get();
        List<List<String>> out = new ArrayList<>();
        try (Reader r = in; CSVParser parser = format.parse(r)) {
            for (CSVRecord rec : parser) {
                List<String> cells = new ArrayList<>(rec.size());
                for (String cell : rec) cells.add(literalQuotes ? unquote(cell) : cell);
                out.add(cells);
                if (lenient && out.size() >= HEADER_SCAN_RECORDS) break;
            }
        } catch (IOException e) {
            if (!lenient) throw new UncheckedIOException(e);
        } catch (UncheckedIOException e) {
            if (!lenient) throw e;
        }
        return out;
    }

    private static String unquote(String cell) {
        if (cell.length() >= 2 && cell.charAt(0) == '"' && cell.charAt(cell.length() - 1) == '"') {
            return cell.substring(1, cell.length() - 1).trim();
        }
        return cell;
    }

    /** The first {@value #HEADER_SCAN_RECORDS} non-blank lines, decoded. */
    private static String head(byte[] bytes) {
        int lines = 0;
        boolean blank = true;
        int end = bytes.length;
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if (b == '\n') {
                if (!blank && ++lines >= HEADER_SCAN_RECORDS) {
                    end = i + 1;
                    break;
                }
                blank = true;
            } else if (b != '\r' && b != ' ' && b != '\t') {
                blank = false;
            }
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
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

    private static char dominantDelimiter(String line) {
        int pipe = countOutsideQuotes(line, '|');
        int semi = countOutsideQuotes(line, ';');
        int comma = countOutsideQuotes(line, ',');
        int tab = countOutsideQuotes(line, '\t');
        if (pipe >= semi && pipe >= comma && pipe >= tab && pipe > 0) return '|';
        if (semi >= comma && semi >= tab) return ';';
        if (tab >= comma) return '\t';
        return ',';
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
