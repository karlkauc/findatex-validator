package com.findatex.validator.report;

import com.findatex.validator.domain.RawCell;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class AnnotatedSourceSheetWriter {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedSourceSheetWriter.class);

    private static final int MAX_FINDING_MSG = 400;
    private static final int MAX_COMMENT_TEXT = 1500;

    private record CellKey(int row, int col) {}

    private AnnotatedSourceSheetWriter() {}

    static void write(Sheet sheet, QualityReport report,
                      CellStyle headerStyle,
                      CellStyle err, CellStyle warn, CellStyle info) {
        SourceMirror.SourceData src;
        try {
            src = SourceMirror.read(report.file());
        } catch (IOException ex) {
            log.warn("Could not re-read source file for Annotated Source tab: {}", ex.toString());
            Row rr = sheet.createRow(0);
            rr.createCell(0).setCellValue(
                    "Original file no longer available — see the Findings tab for details.");
            return;
        }
        if (src.rows().isEmpty()) {
            Row rr = sheet.createRow(0);
            rr.createCell(0).setCellValue("Original file is empty.");
            return;
        }

        Workbook wb = sheet.getWorkbook();
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        CreationHelper helper = wb.getCreationHelper();

        Map<Integer, TptRow> rowsByLogical = new HashMap<>();
        Map<Integer, Integer> mirrorRowToLogical = new HashMap<>();
        for (TptRow tr : report.file().rows()) {
            rowsByLogical.put(tr.rowIndex(), tr);
            Iterator<RawCell> it = tr.all().values().iterator();
            if (it.hasNext()) {
                mirrorRowToLogical.put(it.next().sourceRow() - 1, tr.rowIndex());
            }
        }

        Map<CellKey, List<Finding>> byCell = new HashMap<>();
        Map<Integer, Severity> worstByRow = new HashMap<>();
        for (Finding f : report.findings()) {
            if (f.rowIndex() == null) continue;
            TptRow tr = rowsByLogical.get(f.rowIndex());
            if (tr == null) continue;
            Iterator<RawCell> it = tr.all().values().iterator();
            if (!it.hasNext()) continue;
            int mirrorRow = it.next().sourceRow() - 1;
            int mirrorCol;
            if (f.fieldNum() != null) {
                RawCell rc = tr.all().get(f.fieldNum());
                mirrorCol = rc == null ? 0 : rc.sourceCol();
            } else {
                mirrorCol = 0;
            }
            byCell.computeIfAbsent(new CellKey(mirrorRow, mirrorCol), k -> new ArrayList<>()).add(f);
            worstByRow.merge(mirrorRow, f.severity(), AnnotatedSourceSheetWriter::worse);
        }

        int dataWidth = 0;
        for (List<SourceMirror.SourceCell> row : src.rows()) dataWidth = Math.max(dataWidth, row.size());
        int totalCols = dataWidth + 1;

        StyleResolver styles = new StyleResolver(wb, err, warn, info);

        for (int rIdx = 0; rIdx < src.rows().size(); rIdx++) {
            Row rr = sheet.createRow(rIdx);
            List<SourceMirror.SourceCell> srcRow = src.rows().get(rIdx);
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
            if (!isHeaderRow) {
                Severity rowSeverity = worstByRow.get(rIdx);
                if (rowSeverity != null) {
                    zeile.setCellStyle(styleFor(rowSeverity, err, warn, info));
                }
            }
            List<Finding> rowLevelFindings = byCell.get(new CellKey(rIdx, 0));
            if (rowLevelFindings != null && !rowLevelFindings.isEmpty()) {
                attachComment(drawing, helper, zeile, rowLevelFindings);
            }

            for (int c = 0; c < srcRow.size(); c++) {
                int mirrorCol = c + 1;
                Cell cell = rr.createCell(mirrorCol);
                SourceMirror.SourceCell sc = srcRow.get(c);
                writeTypedValue(cell, sc, isHeaderRow);
                List<Finding> findings = byCell.get(new CellKey(rIdx, mirrorCol));
                CellStyle target = resolveStyle(styles, headerStyle, sc, findings, isHeaderRow);
                if (target != null) cell.setCellStyle(target);
                if (findings != null && !findings.isEmpty()) {
                    attachComment(drawing, helper, cell, findings);
                }
            }
        }

        sheet.createFreezePane(1, src.headerRowIndex() + 1);
        sheet.setColumnWidth(0, 2500);
        for (int c = 1; c < totalCols; c++) sheet.setColumnWidth(c, 4500);
    }

    private static void writeTypedValue(Cell cell, SourceMirror.SourceCell sc, boolean isHeaderRow) {
        if (isHeaderRow) {
            cell.setCellValue(sc.asText());
            return;
        }
        switch (sc.kind()) {
            case STRING -> cell.setCellValue(sc.asText());
            case NUMERIC -> cell.setCellValue(((Double) sc.value()).doubleValue());
            case DATE -> {
                LocalDateTime dt = (LocalDateTime) sc.value();
                cell.setCellValue(Date.from(dt.atZone(ZoneId.systemDefault()).toInstant()));
            }
            case BOOLEAN -> cell.setCellValue(((Boolean) sc.value()).booleanValue());
            case BLANK -> cell.setBlank();
        }
    }

    private static CellStyle resolveStyle(StyleResolver styles, CellStyle headerStyle,
                                          SourceMirror.SourceCell sc, List<Finding> findings,
                                          boolean isHeaderRow) {
        if (isHeaderRow) return headerStyle;
        boolean hasFinding = findings != null && !findings.isEmpty();
        if (hasFinding) {
            return styles.findingStyle(worstSeverity(findings), sc.dataFormat());
        }
        if (needsFormat(sc)) {
            return styles.plainFormatStyle(sc.dataFormat());
        }
        return null;
    }

    private static boolean needsFormat(SourceMirror.SourceCell sc) {
        if (sc.kind() == SourceMirror.CellKind.DATE) return sc.dataFormat() != null && !sc.dataFormat().isEmpty();
        if (sc.kind() == SourceMirror.CellKind.NUMERIC) {
            String fmt = sc.dataFormat();
            return fmt != null && !fmt.isEmpty() && !"General".equalsIgnoreCase(fmt);
        }
        return false;
    }

    private static final class StyleResolver {
        private final Workbook wb;
        private final CellStyle err;
        private final CellStyle warn;
        private final CellStyle info;
        private final Map<String, CellStyle> plain = new HashMap<>();
        private final Map<String, CellStyle> findingStyles = new HashMap<>();

        StyleResolver(Workbook wb, CellStyle err, CellStyle warn, CellStyle info) {
            this.wb = wb;
            this.err = err;
            this.warn = warn;
            this.info = info;
        }

        CellStyle plainFormatStyle(String fmt) {
            return plain.computeIfAbsent(fmt, this::buildPlain);
        }

        CellStyle findingStyle(Severity severity, String fmt) {
            String key = severity.name() + ':' + (fmt == null ? "" : fmt);
            return findingStyles.computeIfAbsent(key, k -> buildFinding(severity, fmt));
        }

        private CellStyle buildPlain(String fmt) {
            CellStyle s = wb.createCellStyle();
            s.setDataFormat(wb.createDataFormat().getFormat(fmt));
            return s;
        }

        private CellStyle buildFinding(Severity severity, String fmt) {
            CellStyle base = switch (severity) {
                case ERROR -> err;
                case WARNING -> warn;
                case INFO -> info;
            };
            if (fmt == null || fmt.isEmpty() || "General".equalsIgnoreCase(fmt)) {
                return base;
            }
            CellStyle s = wb.createCellStyle();
            s.cloneStyleFrom(base);
            s.setDataFormat(wb.createDataFormat().getFormat(fmt));
            return s;
        }
    }

    private static Severity worstSeverity(List<Finding> findings) {
        Severity worst = Severity.INFO;
        for (Finding f : findings) {
            if (f.severity() == Severity.ERROR) return Severity.ERROR;
            if (f.severity() == Severity.WARNING) worst = Severity.WARNING;
        }
        return worst;
    }

    private static Severity worse(Severity a, Severity b) {
        if (a == Severity.ERROR || b == Severity.ERROR) return Severity.ERROR;
        if (a == Severity.WARNING || b == Severity.WARNING) return Severity.WARNING;
        return Severity.INFO;
    }

    private static CellStyle styleFor(Severity sev, CellStyle err, CellStyle warn, CellStyle info) {
        return switch (sev) {
            case ERROR -> err;
            case WARNING -> warn;
            case INFO -> info;
        };
    }

    private static void attachComment(Drawing<?> drawing, CreationHelper helper,
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
