package com.findatex.validator.ingest;

import com.findatex.validator.domain.RawCell;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.SpecCatalog;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class XlsxLoader {

    private static final Logger log = LoggerFactory.getLogger(XlsxLoader.class);

    private final SpecCatalog catalog;

    public XlsxLoader(SpecCatalog catalog) {
        this.catalog = catalog;
    }

    public TptFile load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return load(in, file, null);
        }
    }

    public TptFile load(InputStream in, String filename) throws IOException {
        // Web upload path: buffer the bytes once so the report writer can re-read the source
        // for the Annotated-Source tab without going back to the (now-deleted) tempfile.
        byte[] bytes = in.readAllBytes();
        Path source = Path.of(filename == null || filename.isBlank() ? "uploaded.xlsx" : filename);
        return load(new java.io.ByteArrayInputStream(bytes), source, bytes);
    }

    private TptFile load(InputStream in, Path source, byte[] sourceBytes) throws IOException {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = pickSheet(wb);
            if (sheet == null || sheet.getLastRowNum() < 0) {
                return new TptFile(source, "xlsx", List.of(), Map.of(), List.of(), List.of(), sourceBytes);
            }

            int headerRowIdx = findHeaderRow(sheet);
            Row headerRow = sheet.getRow(headerRowIdx);
            List<String> headers = readHeaderRow(headerRow);

            List<String> unmapped = new ArrayList<>();
            Map<Integer, String> map = new HeaderMapper(catalog).map(headers, unmapped);

            List<TptRow> rows = new ArrayList<>();
            int dataRowCount = 0;
            for (int rIdx = headerRowIdx + 1; rIdx <= sheet.getLastRowNum(); rIdx++) {
                Row r = sheet.getRow(rIdx);
                if (r == null || isRowBlank(r)) continue;
                dataRowCount++;
                TptRow row = new TptRow(dataRowCount);
                for (Map.Entry<Integer, String> e : map.entrySet()) {
                    int col = e.getKey();
                    String v = stringValue(r.getCell(col));
                    if (v == null) v = "";
                    row.put(e.getValue(), new RawCell(v, rIdx + 1, col + 1));
                }
                rows.add(row);
            }
            log.info("Loaded XLSX {} ({} rows, {} mapped fields, {} unmapped headers)",
                    source.getFileName(), rows.size(), map.size(), unmapped.size());
            return new TptFile(source, "xlsx", headers, map, unmapped, rows, sourceBytes);
        }
    }

    private static Sheet pickSheet(Workbook wb) {
        // Always load the first sheet, regardless of name. FinDatEx files typically
        // place the data sheet at index 0; the catalog's header matching does the
        // template-aware filtering downstream.
        return wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
    }

    /** Find the first row that contains an obvious TPT header (matches a known field). */
    private int findHeaderRow(Sheet sheet) {
        int last = Math.min(sheet.getLastRowNum(), 50);
        for (int i = 0; i <= last; i++) {
            Row r = sheet.getRow(i);
            if (r == null) continue;
            int matches = 0;
            for (int c = 0; c < Math.min(r.getLastCellNum(), 200); c++) {
                String v = stringValue(r.getCell(c));
                if (v != null && !v.isBlank() && catalog.matchHeader(v).isPresent()) matches++;
                if (matches >= 3) return i;
            }
        }
        return 0; // fallback
    }

    private static List<String> readHeaderRow(Row row) {
        List<String> headers = new ArrayList<>();
        if (row == null) return headers;
        int last = row.getLastCellNum();
        for (int c = 0; c < last; c++) {
            String v = stringValue(row.getCell(c));
            headers.add(v == null ? "" : v.trim());
        }
        return headers;
    }

    private static boolean isRowBlank(Row r) {
        for (int c = 0; c < r.getLastCellNum(); c++) {
            String v = stringValue(r.getCell(c));
            if (v != null && !v.trim().isEmpty()) return false;
        }
        return true;
    }

    private static String stringValue(Cell cell) {
        if (cell == null) return "";
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        LocalDateTime dt = cell.getLocalDateTimeCellValue();
                        if (dt.toLocalTime().toSecondOfDay() == 0) {
                            yield dt.toLocalDate().toString();
                        }
                        yield dt.toString();
                    }
                    double d = cell.getNumericCellValue();
                    if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                        yield Long.toString((long) d);
                    }
                    yield Double.toString(d);
                }
                case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                case FORMULA -> formulaValue(cell);
                case BLANK, ERROR, _NONE -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    private static String formulaValue(Cell cell) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> {
                    double d = cell.getNumericCellValue();
                    if (d == Math.floor(d) && !Double.isInfinite(d)) yield Long.toString((long) d);
                    yield Double.toString(d);
                }
                case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }
}
