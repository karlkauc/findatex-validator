package com.findatex.validator.report;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.FindingContext;
import com.findatex.validator.validation.Severity;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class XlsxReportWriter {

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

            writeSummary(wb, report, header, pct);
            writeScores(wb, report, header, pct);
            writeFindings(wb, report, header, err, warn);
            writeFieldCoverage(wb, report, header);
            writePerPosition(wb, report, header, ok, warn, err);

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
}
