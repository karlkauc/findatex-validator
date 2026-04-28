package com.findatex.validator.report;

import com.findatex.validator.domain.RawCell;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.FindingContext;
import com.findatex.validator.validation.Severity;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class XlsxReportWriter {

    private static final Logger log = LoggerFactory.getLogger(XlsxReportWriter.class);

    private final SpecCatalog catalog;
    private final ProfileSet profileSet;

    public XlsxReportWriter(SpecCatalog catalog, ProfileSet profileSet) {
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.profileSet = java.util.Objects.requireNonNull(profileSet, "profileSet");
    }

    public void write(QualityReport report, Path out) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             OutputStream os = Files.newOutputStream(out)) {
            CellStyle header = headerStyle(wb);
            CellStyle pct = percentStyle(wb);
            CellStyle err = colourStyle(wb, IndexedColors.ROSE.getIndex());
            CellStyle warn = colourStyle(wb, IndexedColors.LIGHT_YELLOW.getIndex());
            CellStyle ok = colourStyle(wb, IndexedColors.LIGHT_GREEN.getIndex());
            CellStyle info = colourStyle(wb, IndexedColors.PALE_BLUE.getIndex());

            writeSummary(wb, report, header, pct);
            writeScores(wb, report, header, pct);
            writeFindings(wb, report, header, err, warn);
            writeFieldCoverage(wb, report, header);
            writePerPosition(wb, report, header, ok, warn, err);
            writeAnnotatedSource(wb, report, header, err, warn, info);

            wb.write(os);
        }
    }

    private static void writeSummary(Workbook wb, QualityReport r, CellStyle header, CellStyle pct) {
        Sheet s = wb.createSheet("Summary");
        int row = 0;
        addRow(s, row++, header, "TPT V7 Quality Report");
        addRow(s, row++, null,   "");
        addRow(s, row++, null,   "Source file",      r.file().source().toString());
        addRow(s, row++, null,   "Format",           r.file().inputFormat());
        addRow(s, row++, null,   "Generated",        r.generatedAt().toString());
        addRow(s, row++, null,   "Rows",             Integer.toString(r.file().rows().size()));
        addRow(s, row++, null,   "Mapped fields",    Integer.toString(r.file().headerToNumKey().size()));
        addRow(s, row++, null,   "Unmapped headers", String.join(", ", r.file().unmappedHeaders()));
        addRow(s, row++, null,   "Active profiles",
                r.activeProfiles().stream().map(ProfileKey::displayName).collect(Collectors.joining(", ")));
        row++;
        addRow(s, row++, header, "Findings by severity");
        long e = r.findings().stream().filter(f -> f.severity() == Severity.ERROR).count();
        long w = r.findings().stream().filter(f -> f.severity() == Severity.WARNING).count();
        long i = r.findings().stream().filter(f -> f.severity() == Severity.INFO).count();
        addRow(s, row++, null, "ERROR",   Long.toString(e));
        addRow(s, row++, null, "WARNING", Long.toString(w));
        addRow(s, row++, null, "INFO",    Long.toString(i));
        for (int c = 0; c < 3; c++) s.autoSizeColumn(c);
    }

    private static void writeScores(Workbook wb, QualityReport r, CellStyle header, CellStyle pct) {
        Sheet s = wb.createSheet("Scores");
        int row = 0;
        addRow(s, row++, header, "Category", "Score");
        for (Map.Entry<ScoreCategory, Double> e : r.scores().entrySet()) {
            Row rr = s.createRow(row++);
            rr.createCell(0).setCellValue(e.getKey().name());
            org.apache.poi.ss.usermodel.Cell c = rr.createCell(1);
            c.setCellValue(e.getValue());
            c.setCellStyle(pct);
        }
        row++;
        addRow(s, row++, header, "Per-profile scores");
        addRow(s, row++, header, "Profile", "Category", "Score");
        for (Map.Entry<ProfileKey, Map<ScoreCategory, Double>> pe : r.perProfileScores().entrySet()) {
            for (Map.Entry<ScoreCategory, Double> ce : pe.getValue().entrySet()) {
                Row rr = s.createRow(row++);
                rr.createCell(0).setCellValue(pe.getKey().displayName());
                rr.createCell(1).setCellValue(ce.getKey().name());
                org.apache.poi.ss.usermodel.Cell v = rr.createCell(2);
                v.setCellValue(ce.getValue());
                v.setCellStyle(pct);
            }
        }
        for (int c = 0; c < 4; c++) s.autoSizeColumn(c);
    }

    private static void writeFindings(Workbook wb, QualityReport r,
                                      CellStyle header, CellStyle err, CellStyle warn) {
        Sheet s = wb.createSheet("Findings");
        int row = 0;
        addRow(s, row++, header,
                "Severity", "Profile", "Rule",
                "Fund ID", "Fund name", "Valuation date",
                "Field#", "Field name", "Row", "Instrument code", "Instrument name",
                "Weight", "Value", "Message");
        for (Finding f : r.findings()) {
            Row rr = s.createRow(row++);
            CellStyle style = switch (f.severity()) {
                case ERROR -> err;
                case WARNING -> warn;
                case INFO -> null;
            };
            FindingContext ctx = f.context() == null ? FindingContext.EMPTY : f.context();

            org.apache.poi.ss.usermodel.Cell c0 = rr.createCell(0);
            c0.setCellValue(f.severity().name());
            if (style != null) c0.setCellStyle(style);
            rr.createCell(1).setCellValue(f.profile() == null ? "" : f.profile().displayName());
            rr.createCell(2).setCellValue(f.ruleId());
            rr.createCell(3).setCellValue(nz(ctx.portfolioId()));
            rr.createCell(4).setCellValue(nz(ctx.portfolioName()));
            rr.createCell(5).setCellValue(nz(ctx.valuationDate()));
            rr.createCell(6).setCellValue(nz(f.fieldNum()));
            rr.createCell(7).setCellValue(nz(f.fieldName()));
            rr.createCell(8).setCellValue(f.rowIndex() == null ? "" : f.rowIndex().toString());
            rr.createCell(9).setCellValue(nz(ctx.instrumentCode()));
            rr.createCell(10).setCellValue(nz(ctx.instrumentName()));
            rr.createCell(11).setCellValue(nz(ctx.valuationWeight()));
            rr.createCell(12).setCellValue(nz(f.value()));
            rr.createCell(13).setCellValue(nz(f.message()));
        }
        s.createFreezePane(3, 1);
        for (int c = 0; c < 14; c++) s.autoSizeColumn(c);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private void writeFieldCoverage(Workbook wb, QualityReport r, CellStyle header) {
        Sheet s = wb.createSheet("Field Coverage");
        int row = 0;
        java.util.List<ProfileKey> profiles = profileSet.all();
        java.util.List<String> headers = new java.util.ArrayList<>();
        headers.add("Field#");
        headers.add("NUM_DATA");
        headers.add("FunDataXML path");
        for (ProfileKey p : profiles) headers.add(p.displayName());
        headers.add("Present");
        headers.add("Missing");
        headers.add("Invalid");
        addRow(s, row++, header, headers.toArray(new String[0]));

        Map<String, long[]> byField = new HashMap<>(); // numKey -> [present, missing, invalid]
        for (FieldSpec spec : catalog.fields()) byField.put(spec.numKey(), new long[3]);

        for (TptRow tr : r.file().rows()) {
            for (Map.Entry<String, com.findatex.validator.domain.RawCell> e : tr.all().entrySet()) {
                long[] arr = byField.get(e.getKey());
                if (arr == null) continue;
                if (e.getValue().isEmpty()) arr[1]++;
                else arr[0]++;
            }
        }
        for (Finding f : r.findings()) {
            if (f.fieldNum() == null || f.severity() != Severity.ERROR) continue;
            if (f.ruleId().startsWith("FORMAT/")) {
                long[] arr = byField.get(f.fieldNum());
                if (arr != null) arr[2]++;
            }
        }
        for (FieldSpec spec : catalog.fields()) {
            long[] c = byField.get(spec.numKey());
            Row rr = s.createRow(row++);
            int col = 0;
            rr.createCell(col++).setCellValue(spec.numKey());
            rr.createCell(col++).setCellValue(spec.numData());
            rr.createCell(col++).setCellValue(spec.fundXmlPath() == null ? "" : spec.fundXmlPath());
            for (ProfileKey p : profiles) {
                rr.createCell(col++).setCellValue(spec.flag(p).name());
            }
            rr.createCell(col++).setCellValue(c == null ? 0 : c[0]);
            rr.createCell(col++).setCellValue(c == null ? 0 : c[1]);
            rr.createCell(col++).setCellValue(c == null ? 0 : c[2]);
        }
        s.createFreezePane(2, 1);
        int totalCols = 3 + profiles.size() + 3;
        for (int c = 0; c < totalCols; c++) s.autoSizeColumn(c);
    }

    private static void writePerPosition(Workbook wb, QualityReport r,
                                         CellStyle header, CellStyle ok, CellStyle warn, CellStyle err) {
        Sheet s = wb.createSheet("Per Position");
        // Per-position summary: row index + count of errors/warnings on that row.
        Map<Integer, long[]> byRow = new HashMap<>();
        for (Finding f : r.findings()) {
            if (f.rowIndex() == null) continue;
            long[] arr = byRow.computeIfAbsent(f.rowIndex(), k -> new long[2]);
            if (f.severity() == Severity.ERROR) arr[0]++;
            else if (f.severity() == Severity.WARNING) arr[1]++;
        }
        int row = 0;
        addRow(s, row++, header, "Row", "Errors", "Warnings", "Status");
        for (TptRow tr : r.file().rows()) {
            long[] arr = byRow.getOrDefault(tr.rowIndex(), new long[2]);
            Row rr = s.createRow(row++);
            rr.createCell(0).setCellValue(tr.rowIndex());
            rr.createCell(1).setCellValue(arr[0]);
            rr.createCell(2).setCellValue(arr[1]);
            org.apache.poi.ss.usermodel.Cell status = rr.createCell(3);
            if (arr[0] > 0) {
                status.setCellValue("ERROR");
                status.setCellStyle(err);
            } else if (arr[1] > 0) {
                status.setCellValue("WARNING");
                status.setCellStyle(warn);
            } else {
                status.setCellValue("OK");
                status.setCellStyle(ok);
            }
        }
        s.createFreezePane(0, 1);
        for (int c = 0; c < 4; c++) s.autoSizeColumn(c);
    }

    private static void addRow(Sheet s, int rowIdx, CellStyle style, String... values) {
        Row r = s.createRow(rowIdx);
        for (int c = 0; c < values.length; c++) {
            org.apache.poi.ss.usermodel.Cell cell = r.createCell(c);
            cell.setCellValue(values[c]);
            if (style != null) cell.setCellStyle(style);
        }
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.LEFT);
        return s;
    }

    private static CellStyle percentStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
        return s;
    }

    private static CellStyle colourStyle(Workbook wb, short colour) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(colour);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    // --- Annotated Source tab -------------------------------------------------

    private record CellKey(int row, int col) {}

    private static final int MAX_FINDING_MSG = 400;
    private static final int MAX_COMMENT_TEXT = 1500;

    private static void writeAnnotatedSource(Workbook wb, QualityReport r,
                                             CellStyle headerStyle,
                                             CellStyle err, CellStyle warn, CellStyle info) {
        Sheet s = wb.createSheet("Annotated Source");

        SourceMirror.SourceData src;
        try {
            src = SourceMirror.read(r.file());
        } catch (IOException ex) {
            log.warn("Could not re-read source file for Annotated Source tab: {}", ex.toString());
            Row rr = s.createRow(0);
            rr.createCell(0).setCellValue(
                    "Original file no longer available — see the Findings tab for details.");
            return;
        }
        if (src.rows().isEmpty()) {
            Row rr = s.createRow(0);
            rr.createCell(0).setCellValue("Original file is empty.");
            return;
        }

        Drawing<?> drawing = s.createDrawingPatriarch();
        CreationHelper helper = wb.getCreationHelper();

        // Map each TptRow back to its 0-based source-row index in the mirror.
        Map<Integer, TptRow> rowsByLogical = new HashMap<>();
        Map<Integer, Integer> mirrorRowToLogical = new HashMap<>();
        for (TptRow tr : r.file().rows()) {
            rowsByLogical.put(tr.rowIndex(), tr);
            Iterator<RawCell> it = tr.all().values().iterator();
            if (it.hasNext()) {
                mirrorRowToLogical.put(it.next().sourceRow() - 1, tr.rowIndex());
            }
        }

        // Bucket findings by mirror cell. Mirror columns are shifted by +1 to
        // make room for the leftmost "Zeile" helper column.
        Map<CellKey, List<Finding>> byCell = new HashMap<>();
        for (Finding f : r.findings()) {
            if (f.rowIndex() == null) continue; // portfolio-level → skip on this tab
            TptRow tr = rowsByLogical.get(f.rowIndex());
            if (tr == null) continue;
            Iterator<RawCell> it = tr.all().values().iterator();
            if (!it.hasNext()) continue;
            int mirrorRow = it.next().sourceRow() - 1;
            int mirrorCol;
            if (f.fieldNum() != null) {
                RawCell rc = tr.all().get(f.fieldNum());
                mirrorCol = rc == null ? 0 : rc.sourceCol(); // sourceCol is 1-based; shift cancels out
            } else {
                mirrorCol = 0; // cross-field row finding → Zeile column
            }
            byCell.computeIfAbsent(new CellKey(mirrorRow, mirrorCol), k -> new ArrayList<>()).add(f);
        }

        int dataWidth = 0;
        for (List<String> row : src.rows()) dataWidth = Math.max(dataWidth, row.size());
        int totalCols = dataWidth + 1;

        for (int rIdx = 0; rIdx < src.rows().size(); rIdx++) {
            Row rr = s.createRow(rIdx);
            List<String> srcRow = src.rows().get(rIdx);
            boolean isHeaderRow = rIdx == src.headerRowIndex();

            Cell zeile = rr.createCell(0);
            if (isHeaderRow) {
                zeile.setCellValue("Row");
                zeile.setCellStyle(headerStyle);
            } else if (mirrorRowToLogical.containsKey(rIdx)) {
                zeile.setCellValue(mirrorRowToLogical.get(rIdx));
            } else {
                zeile.setCellValue("");
            }
            applyFindings(s, drawing, helper, zeile, byCell.get(new CellKey(rIdx, 0)),
                    err, warn, info);

            for (int c = 0; c < srcRow.size(); c++) {
                int mirrorCol = c + 1;
                Cell cell = rr.createCell(mirrorCol);
                String v = srcRow.get(c);
                cell.setCellValue(v == null ? "" : v);
                if (isHeaderRow) cell.setCellStyle(headerStyle);
                applyFindings(s, drawing, helper, cell, byCell.get(new CellKey(rIdx, mirrorCol)),
                        err, warn, info);
            }
        }

        s.createFreezePane(1, src.headerRowIndex() + 1);
        s.setColumnWidth(0, 2500);
        for (int c = 1; c < totalCols; c++) s.setColumnWidth(c, 4500);
    }

    private static void applyFindings(Sheet s, Drawing<?> drawing, CreationHelper helper,
                                      Cell cell, List<Finding> findings,
                                      CellStyle err, CellStyle warn, CellStyle info) {
        if (findings == null || findings.isEmpty()) return;
        cell.setCellStyle(styleFor(worstSeverity(findings), err, warn, info));
        attachComment(s, drawing, helper, cell, findings);
    }

    private static Severity worstSeverity(List<Finding> findings) {
        Severity worst = Severity.INFO;
        for (Finding f : findings) {
            if (f.severity() == Severity.ERROR) return Severity.ERROR;
            if (f.severity() == Severity.WARNING) worst = Severity.WARNING;
        }
        return worst;
    }

    private static CellStyle styleFor(Severity sev, CellStyle err, CellStyle warn, CellStyle info) {
        return switch (sev) {
            case ERROR -> err;
            case WARNING -> warn;
            case INFO -> info;
        };
    }

    private static void attachComment(Sheet sheet, Drawing<?> drawing, CreationHelper helper,
                                      Cell cell, List<Finding> findings) {
        ClientAnchor a = helper.createClientAnchor();
        a.setCol1(cell.getColumnIndex());
        a.setCol2(cell.getColumnIndex() + 3);
        a.setRow1(cell.getRowIndex());
        a.setRow2(cell.getRowIndex() + 5);
        Comment c = drawing.createCellComment(a);
        c.setString(helper.createRichTextString(findingsToCommentText(findings)));
        c.setAuthor("FinDatEx Validator");
        cell.setCellComment(c);
    }

    private static String findingsToCommentText(List<Finding> findings) {
        List<Finding> sorted = new ArrayList<>(findings);
        sorted.sort((x, y) -> {
            int sx = severityOrder(x.severity());
            int sy = severityOrder(y.severity());
            if (sx != sy) return Integer.compare(sx, sy);
            return String.valueOf(x.ruleId()).compareTo(String.valueOf(y.ruleId()));
        });
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            Finding f = sorted.get(i);
            if (i > 0) sb.append("\n\n");
            String msg = f.message() == null ? "" : f.message();
            if (msg.length() > MAX_FINDING_MSG) msg = msg.substring(0, MAX_FINDING_MSG) + "…";
            sb.append('[').append(f.severity().name()).append("] ");
            if (f.ruleId() != null) sb.append(f.ruleId()).append(" — ");
            sb.append(msg);
            if (sb.length() > MAX_COMMENT_TEXT) {
                sb.setLength(MAX_COMMENT_TEXT);
                sb.append("\n…(truncated)");
                break;
            }
        }
        return sb.toString();
    }

    private static int severityOrder(Severity sev) {
        return switch (sev) {
            case ERROR -> 0;
            case WARNING -> 1;
            case INFO -> 2;
        };
    }
}
