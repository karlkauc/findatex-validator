package com.findatex.validator.report;

import com.findatex.validator.domain.TptFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

final class SourceMirror {

    enum CellKind { STRING, NUMERIC, DATE, BOOLEAN, BLANK }

    record SourceCell(CellKind kind, Object value, String dataFormat) {
        static final SourceCell BLANK = new SourceCell(CellKind.BLANK, null, null);

        static SourceCell text(String s) {
            return new SourceCell(CellKind.STRING, s == null ? "" : s, null);
        }

        String asText() {
            return switch (kind) {
                case STRING -> value == null ? "" : (String) value;
                case NUMERIC -> {
                    double d = (Double) value;
                    if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                        yield Long.toString((long) d);
                    }
                    yield Double.toString(d);
                }
                case DATE -> {
                    LocalDateTime dt = (LocalDateTime) value;
                    if (dt.toLocalTime().toSecondOfDay() == 0) {
                        yield dt.toLocalDate().toString();
                    }
                    yield dt.toString();
                }
                case BOOLEAN -> Boolean.toString((Boolean) value);
                case BLANK -> "";
            };
        }
    }

    record SourceData(List<List<SourceCell>> rows, int headerRowIndex) {}

    static SourceData read(TptFile file) throws IOException {
        String fmt = file.inputFormat();
        if ("xlsx".equals(fmt)) return readXlsx(file);
        if ("csv".equals(fmt)) return readCsv(file);
        throw new IOException("Unsupported input format: " + fmt);
    }

    private static SourceData readXlsx(TptFile file) throws IOException {
        try (InputStream in = Files.newInputStream(file.source());
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null || sheet.getLastRowNum() < 0) {
                return new SourceData(List.of(), 0);
            }
            int last = sheet.getLastRowNum();
            int width = 0;
            for (int r = 0; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row != null) width = Math.max(width, row.getLastCellNum());
            }
            int headerRowIndex = locateHeader(sheet, file.rawHeaders(), last);
            List<List<SourceCell>> rows = new ArrayList<>(last + 1);
            for (int r = 0; r <= last; r++) {
                Row row = sheet.getRow(r);
                List<SourceCell> cells = new ArrayList<>(width);
                if (row == null) {
                    for (int c = 0; c < width; c++) cells.add(SourceCell.BLANK);
                } else {
                    int rowWidth = Math.max(width, row.getLastCellNum());
                    for (int c = 0; c < rowWidth; c++) {
                        cells.add(readCell(row.getCell(c)));
                    }
                }
                rows.add(cells);
            }
            return new SourceData(rows, headerRowIndex);
        }
    }

    private static int locateHeader(Sheet sheet, List<String> rawHeaders, int lastRow) {
        if (rawHeaders == null || rawHeaders.isEmpty()) return 0;
        int scanLimit = Math.min(lastRow, 50);
        for (int r = 0; r <= scanLimit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            boolean match = true;
            for (int c = 0; c < rawHeaders.size(); c++) {
                String expected = rawHeaders.get(c) == null ? "" : rawHeaders.get(c).trim();
                String actual = readCell(row.getCell(c)).asText().trim();
                if (!actual.equals(expected)) { match = false; break; }
            }
            if (match) return r;
        }
        return 0;
    }

    private static SourceData readCsv(TptFile file) throws IOException {
        char delimiter = detectDelimiter(file.source());
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setQuote('"')
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();
        try (Reader r = Files.newBufferedReader(file.source(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(r)) {
            List<CSVRecord> records = parser.getRecords();
            int width = 0;
            for (CSVRecord rec : records) width = Math.max(width, rec.size());
            List<List<SourceCell>> rows = new ArrayList<>(records.size());
            for (CSVRecord rec : records) {
                List<SourceCell> row = new ArrayList<>(width);
                for (int c = 0; c < width; c++) {
                    row.add(c < rec.size() ? SourceCell.text(rec.get(c)) : SourceCell.BLANK);
                }
                rows.add(row);
            }
            return new SourceData(rows, 0);
        }
    }

    private static char detectDelimiter(Path file) throws IOException {
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

    private static SourceCell readCell(Cell cell) {
        if (cell == null) return SourceCell.BLANK;
        try {
            return switch (cell.getCellType()) {
                case STRING -> new SourceCell(CellKind.STRING, cell.getStringCellValue(), null);
                case NUMERIC -> {
                    String fmt = cell.getCellStyle() == null ? null
                            : cell.getCellStyle().getDataFormatString();
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield new SourceCell(CellKind.DATE, cell.getLocalDateTimeCellValue(), fmt);
                    }
                    yield new SourceCell(CellKind.NUMERIC, cell.getNumericCellValue(), fmt);
                }
                case BOOLEAN -> new SourceCell(CellKind.BOOLEAN, cell.getBooleanCellValue(), null);
                case FORMULA -> readFormulaCell(cell);
                case BLANK, ERROR, _NONE -> SourceCell.BLANK;
            };
        } catch (Exception e) {
            return SourceCell.BLANK;
        }
    }

    private static SourceCell readFormulaCell(Cell cell) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> new SourceCell(CellKind.STRING, cell.getStringCellValue(), null);
                case NUMERIC -> {
                    String fmt = cell.getCellStyle() == null ? null
                            : cell.getCellStyle().getDataFormatString();
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield new SourceCell(CellKind.DATE, cell.getLocalDateTimeCellValue(), fmt);
                    }
                    yield new SourceCell(CellKind.NUMERIC, cell.getNumericCellValue(), fmt);
                }
                case BOOLEAN -> new SourceCell(CellKind.BOOLEAN, cell.getBooleanCellValue(), null);
                default -> SourceCell.BLANK;
            };
        } catch (Exception e) {
            return SourceCell.BLANK;
        }
    }

    private SourceMirror() {}
}
