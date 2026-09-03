package com.findatex.validator.report;

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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the "Annotated Source" sheet from an {@link AnnotatedSourceModel}: the original file
 * cell-for-cell with original number/date formats, cells tinted by worst severity and an Excel
 * comment listing the findings. The join logic lives in the model so the desktop view and the
 * sheet stay in lock-step.
 */
final class AnnotatedSourceSheetWriter {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedSourceSheetWriter.class);

    private AnnotatedSourceSheetWriter() {}

    static void write(Sheet sheet, QualityReport report,
                      CellStyle headerStyle,
                      CellStyle err, CellStyle warn, CellStyle info) {
        write(sheet, report, null, headerStyle, err, warn, info);
    }

    /**
     * @param prebuilt an already built model for {@code report}, or {@code null} to build one
     *                 here — callers that need the model for something else (the web JSON side
     *                 artifact) pass it in so the source is re-read only once
     */
    static void write(Sheet sheet, QualityReport report, AnnotatedSourceModel prebuilt,
                      CellStyle headerStyle,
                      CellStyle err, CellStyle warn, CellStyle info) {
        AnnotatedSourceModel model = prebuilt;
        try {
            if (model == null) model = AnnotatedSourceModel.build(report);
        } catch (IOException ex) {
            log.warn("Could not re-read source file for Annotated Source tab: {}", ex.toString());
            Row rr = sheet.createRow(0);
            rr.createCell(0).setCellValue(
                    "Original file no longer available — see the Findings tab for details.");
            return;
        }
        if (model.rows().isEmpty()) {
            Row rr = sheet.createRow(0);
            rr.createCell(0).setCellValue("Original file is empty.");
            return;
        }

        Workbook wb = sheet.getWorkbook();
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        CreationHelper helper = wb.getCreationHelper();
        StyleResolver styles = new StyleResolver(wb, err, warn, info);
        int totalCols = model.width() + 1;

        for (AnnotatedSourceModel.Row mr : model.rows()) {
            Row rr = sheet.createRow(mr.mirrorIndex());
            boolean isHeaderRow = mr.header();

            Cell zeile = rr.createCell(0);
            if (isHeaderRow) {
                zeile.setCellValue("Row");
                zeile.setCellStyle(headerStyle);
            } else if (mr.logicalRow() != null) {
                zeile.setCellValue(mr.logicalRow());
            } else {
                zeile.setCellValue("");
            }
            if (!isHeaderRow && mr.rowSeverity() != null) {
                zeile.setCellStyle(styleFor(mr.rowSeverity(), err, warn, info));
            }
            if (!mr.rowLevelFindings().isEmpty()) {
                attachComment(drawing, helper, zeile, mr.rowLevelFindings());
            }

            List<AnnotatedSourceModel.Cell> cells = mr.cells();
            for (int c = 0; c < cells.size(); c++) {
                AnnotatedSourceModel.Cell ac = cells.get(c);
                Cell cell = rr.createCell(c + 1);
                writeTypedValue(cell, ac.source(), isHeaderRow);
                CellStyle target = resolveStyle(styles, headerStyle, ac, isHeaderRow);
                if (target != null) cell.setCellStyle(target);
                if (ac.hasFindings()) {
                    attachComment(drawing, helper, cell, ac.findings());
                }
            }
        }

        sheet.createFreezePane(1, model.headerRowIndex() + 1);
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
                                          AnnotatedSourceModel.Cell ac, boolean isHeaderRow) {
        if (isHeaderRow) return headerStyle;
        SourceMirror.SourceCell sc = ac.source();
        if (ac.hasFindings()) {
            return styles.findingStyle(ac.severity(), sc.dataFormat());
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
            CellStyle base = styleFor(severity, err, warn, info);
            if (fmt == null || fmt.isEmpty() || "General".equalsIgnoreCase(fmt)) {
                return base;
            }
            CellStyle s = wb.createCellStyle();
            s.cloneStyleFrom(base);
            s.setDataFormat(wb.createDataFormat().getFormat(fmt));
            return s;
        }
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
        c.setString(helper.createRichTextString(AnnotatedSourceModel.describe(findings)));
        c.setAuthor("FinDatEx Validator");
        cell.setCellComment(c);
    }
}
