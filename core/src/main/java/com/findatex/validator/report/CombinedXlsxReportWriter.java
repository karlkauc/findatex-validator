package com.findatex.validator.report;

import com.findatex.validator.AppInfo;
import com.findatex.validator.batch.BatchFileStatus;
import com.findatex.validator.batch.BatchResult;
import com.findatex.validator.batch.BatchSummary;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.FindingContext;
import com.findatex.validator.validation.Severity;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes a single XLSX workbook covering an entire batch run.
 *
 * <p>Sheets, in order:
 * <ol>
 *   <li><b>Batch Summary</b> — header block + one row per file with score breakdown</li>
 *   <li><b>All Findings</b> — every finding from every OK file with a leading {@code File} column</li>
 *   <li><b>Aggregate Field Coverage</b> — Σ Present/Missing/Invalid per field across OK files</li>
 *   <li><b>Per-File Scores</b> — long-format ({@code File, Category, Score}) for pivoting</li>
 *   <li><b>Skipped / Failed Files</b> — only present when the run includes non-OK results</li>
 * </ol>
 *
 * <p>An optional per-file <b>Annotated Source</b> tab can be appended for every OK file via
 * {@link #write(BatchSummary, Path, boolean)} — opt-in, since the resulting workbook can grow
 * substantially. The {@code File} cells in {@code Batch Summary} link directly to the
 * corresponding sheet when this option is enabled.
 */
public final class CombinedXlsxReportWriter {

    private final SpecCatalog catalog;
    private final ProfileSet profileSet;
    private final TemplateVersion templateVersion;
    private final GenerationUi generationUi;

    public CombinedXlsxReportWriter(SpecCatalog catalog,
                                    ProfileSet profileSet,
                                    TemplateVersion templateVersion,
                                    GenerationUi generationUi) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.profileSet = Objects.requireNonNull(profileSet, "profileSet");
        this.templateVersion = Objects.requireNonNull(templateVersion, "templateVersion");
        this.generationUi = Objects.requireNonNull(generationUi, "generationUi");
    }

    public void write(BatchSummary summary, Path out) throws IOException {
        write(summary, out, false);
    }

    public void write(BatchSummary summary, Path out, boolean includeAnnotatedSourcePerFile) throws IOException {
        // Atomic write — see XlsxReportWriter.write for the rationale.
        Path tmp = out.resolveSibling(out.getFileName().toString() + ".tmp");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle header = XlsxStyles.headerStyle(wb);
            CellStyle pct = XlsxStyles.percentStyle(wb);
            CellStyle err = XlsxStyles.colourStyle(wb, IndexedColors.ROSE.getIndex());
            CellStyle warn = XlsxStyles.colourStyle(wb, IndexedColors.LIGHT_YELLOW.getIndex());
            CellStyle info = XlsxStyles.colourStyle(wb, IndexedColors.PALE_BLUE.getIndex());
            CellStyle linkStyle = hyperlinkStyle(wb);

            wb.getProperties().getCustomProperties()
                    .addProperty("Generation-UI", generationUi.label());

            Map<BatchResult, String> annotatedSheetNames =
                    includeAnnotatedSourcePerFile ? planAnnotatedSheetNames(summary) : Map.of();

            writeBatchSummary(wb, summary, header, pct, linkStyle, annotatedSheetNames);
            writeAllFindings(wb, summary, header, err, warn);
            writeAggregateFieldCoverage(wb, summary, header);
            writePerFileScores(wb, summary, header, pct);
            boolean hasFailures = summary.results().stream()
                    .anyMatch(r -> r.status() != BatchFileStatus.OK);
            if (hasFailures) writeSkippedOrFailed(wb, summary, header);

            if (includeAnnotatedSourcePerFile) {
                for (BatchResult r : summary.results()) {
                    String sheetName = annotatedSheetNames.get(r);
                    if (sheetName == null) continue;
                    Sheet sheet = wb.createSheet(sheetName);
                    AnnotatedSourceSheetWriter.write(sheet, r.report(), header, err, warn, info);
                }
            }

            try (OutputStream os = Files.newOutputStream(tmp)) {
                wb.write(os);
            }
            Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(tmp);
            throw ex;
        }
    }

    private static Map<BatchResult, String> planAnnotatedSheetNames(BatchSummary summary) {
        // Excel sheet names: max 31 chars, no `: \ / ? * [ ]`, case-insensitive unique.
        Set<String> taken = new HashSet<>();
        // Pre-seed with the names of the workbook's base sheets to avoid collisions.
        for (String reserved : List.of(
                "Batch Summary", "All Findings", "Aggregate Field Coverage",
                "Per-File Scores", "Skipped or Failed Files")) {
            taken.add(reserved.toLowerCase(Locale.ROOT));
        }
        Map<BatchResult, String> out = new LinkedHashMap<>();
        for (BatchResult r : summary.results()) {
            if (r.status() != BatchFileStatus.OK || r.file() == null || r.report() == null) continue;
            out.put(r, safeSheetName(r.displayName(), taken));
        }
        return out;
    }

    private static String safeSheetName(String displayName, Set<String> taken) {
        String base = displayName == null ? "file" : displayName;
        base = base.replaceAll("[\\\\/?*\\[\\]:]", "_");
        if (base.length() > 31) base = base.substring(0, 31);
        if (base.isEmpty()) base = "file";
        String name = base;
        int n = 2;
        while (taken.contains(name.toLowerCase(Locale.ROOT))) {
            String suffix = " ~" + n++;
            int cut = Math.min(base.length(), 31 - suffix.length());
            name = base.substring(0, cut) + suffix;
        }
        taken.add(name.toLowerCase(Locale.ROOT));
        return name;
    }

    private static CellStyle hyperlinkStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setUnderline(Font.U_SINGLE);
        font.setColor(IndexedColors.BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    private void writeBatchSummary(XSSFWorkbook wb, BatchSummary s,
                                   CellStyle header, CellStyle pct,
                                   CellStyle linkStyle,
                                   Map<BatchResult, String> annotatedSheetNames) {
        Sheet sheet = wb.createSheet("Batch Summary");
        CreationHelper helper = wb.getCreationHelper();
        int row = 0;
        XlsxStyles.addRow(sheet, row++, header,
                AppInfo.applicationName() + " — " + templateVersion.label() + " Batch Quality Report");
        XlsxStyles.addRow(sheet, row++, null,
                "Produced by " + AppInfo.applicationWithVersion());
        XlsxStyles.addRow(sheet, row++, null, "");
        XlsxStyles.addRow(sheet, row++, null, "Started",
                s.startedAt() == null ? "" : s.startedAt().toString());
        XlsxStyles.addRow(sheet, row++, null, "Total elapsed", s.totalElapsed().toString());
        String profileCodes = s.activeProfiles().stream()
                .map(ProfileKey::displayName)
                .sorted()
                .collect(Collectors.joining(", "));
        XlsxStyles.addRow(sheet, row++, null, "Active profiles",
                profileCodes.isEmpty() ? "(none)" : profileCodes);
        XlsxStyles.addRow(sheet, row++, null, "Files validated",
                Integer.toString(s.results().size()));
        XlsxStyles.addRow(sheet, row++, null, "Files OK",
                Long.toString(s.countWithStatus(BatchFileStatus.OK)));
        XlsxStyles.addRow(sheet, row++, null, "Files failed to load",
                Long.toString(s.countWithStatus(BatchFileStatus.LOAD_ERROR)));
        XlsxStyles.addRow(sheet, row++, null, "Cancelled",
                Boolean.toString(s.cancelled()));
        XlsxStyles.addRow(sheet, row++, null, "Aggregate errors",
                Long.toString(s.aggregateErrors()));
        XlsxStyles.addRow(sheet, row++, null, "Aggregate warnings",
                Long.toString(s.aggregateWarnings()));
        XlsxStyles.addRow(sheet, row++, null, "Aggregate info",
                Long.toString(s.aggregateInfos()));

        Row meanRow = sheet.createRow(row++);
        meanRow.createCell(0).setCellValue("Mean OVERALL score");
        OptionalDouble mean = s.aggregateOverallScore();
        if (mean.isPresent()) {
            Cell c = meanRow.createCell(1);
            c.setCellValue(mean.getAsDouble());
            c.setCellStyle(pct);
        } else {
            meanRow.createCell(1).setCellValue("—");
        }
        row++;

        // Per-file table.
        XlsxStyles.addRow(sheet, row++, header,
                "File", "Status", "Rows", "Errors", "Warnings", "Info",
                "OVERALL", "Mandatory", "Format", "Closed lists",
                "Cross-field", "Mean profile compl.", "Time (ms)");
        for (BatchResult r : s.results()) {
            Row rr = sheet.createRow(row++);
            int col = 0;
            Cell fileCell = rr.createCell(col++);
            fileCell.setCellValue(r.displayName());
            String linkedSheet = annotatedSheetNames.get(r);
            if (linkedSheet != null) {
                Hyperlink h = helper.createHyperlink(HyperlinkType.DOCUMENT);
                h.setAddress("'" + linkedSheet + "'!A1");
                fileCell.setHyperlink(h);
                fileCell.setCellStyle(linkStyle);
            }
            rr.createCell(col++).setCellValue(r.status().name());
            if (r.status() == BatchFileStatus.OK && r.report() != null) {
                rr.createCell(col++).setCellValue(r.file().rows().size());
                Map<Severity, Long> bySev = bySeverity(r.findings());
                rr.createCell(col++).setCellValue(bySev.getOrDefault(Severity.ERROR, 0L));
                rr.createCell(col++).setCellValue(bySev.getOrDefault(Severity.WARNING, 0L));
                rr.createCell(col++).setCellValue(bySev.getOrDefault(Severity.INFO, 0L));
                Map<ScoreCategory, Double> scores = r.report().scores();
                col = writeScoreCell(rr, col, scores.get(ScoreCategory.OVERALL), pct);
                col = writeScoreCell(rr, col, scores.get(ScoreCategory.MANDATORY_COMPLETENESS), pct);
                col = writeScoreCell(rr, col, scores.get(ScoreCategory.FORMAT_CONFORMANCE), pct);
                col = writeScoreCell(rr, col, scores.get(ScoreCategory.CLOSED_LIST_CONFORMANCE), pct);
                col = writeScoreCell(rr, col, scores.get(ScoreCategory.CROSS_FIELD_CONSISTENCY), pct);
                double meanProfile = r.report().perProfileScores().values().stream()
                        .mapToDouble(m -> m.getOrDefault(ScoreCategory.PROFILE_COMPLETENESS, 1.0))
                        .average().orElse(1.0);
                Cell mp = rr.createCell(col++);
                mp.setCellValue(meanProfile);
                mp.setCellStyle(pct);
                rr.createCell(col).setCellValue(r.elapsed().toMillis());
            } else {
                // Pad empty cells for layout, but show error message in the File cell tooltip-style.
                // We just leave them blank so Excel renders dashes via the column's lack of value.
                rr.createCell(col++).setCellValue("");
                for (int i = 0; i < 9; i++) rr.createCell(col++).setCellValue("");
                rr.createCell(col).setCellValue(r.elapsed().toMillis());
            }
        }
        sheet.createFreezePane(1, 1);
        for (int c = 0; c < 13; c++) sheet.autoSizeColumn(c);
    }

    private static int writeScoreCell(Row row, int col, Double value, CellStyle pct) {
        Cell c = row.createCell(col);
        if (value == null) {
            c.setCellValue("");
        } else {
            c.setCellValue(value);
            c.setCellStyle(pct);
        }
        return col + 1;
    }

    private void writeAllFindings(XSSFWorkbook wb, BatchSummary s,
                                  CellStyle header, CellStyle err, CellStyle warn) {
        Sheet sheet = wb.createSheet("All Findings");
        int row = 0;
        XlsxStyles.addRow(sheet, row++, header,
                "File", "Severity", "Profile", "Rule",
                "Fund ID", "Fund name", "Valuation date",
                "Field#", "Field name", "Row",
                "Instrument code", "Instrument name", "Weight", "Value", "Message");
        for (BatchResult r : s.results()) {
            if (r.status() != BatchFileStatus.OK) continue;
            for (Finding f : r.findings()) {
                Row rr = sheet.createRow(row++);
                CellStyle style = switch (f.severity()) {
                    case ERROR -> err;
                    case WARNING -> warn;
                    case INFO -> null;
                };
                FindingContext ctx = f.context() == null ? FindingContext.EMPTY : f.context();
                rr.createCell(0).setCellValue(r.displayName());
                Cell sev = rr.createCell(1);
                sev.setCellValue(f.severity().name());
                if (style != null) sev.setCellStyle(style);
                rr.createCell(2).setCellValue(f.profile() == null ? "" : f.profile().displayName());
                rr.createCell(3).setCellValue(nz(f.ruleId()));
                rr.createCell(4).setCellValue(nz(ctx.portfolioId()));
                rr.createCell(5).setCellValue(nz(ctx.portfolioName()));
                rr.createCell(6).setCellValue(nz(ctx.valuationDate()));
                rr.createCell(7).setCellValue(nz(f.fieldNum()));
                rr.createCell(8).setCellValue(nz(f.fieldName()));
                rr.createCell(9).setCellValue(f.rowIndex() == null ? "" : f.rowIndex().toString());
                rr.createCell(10).setCellValue(nz(ctx.instrumentCode()));
                rr.createCell(11).setCellValue(nz(ctx.instrumentName()));
                rr.createCell(12).setCellValue(nz(ctx.valuationWeight()));
                rr.createCell(13).setCellValue(nz(f.value()));
                rr.createCell(14).setCellValue(nz(f.message()));
            }
        }
        if (row > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, row - 1, 0, 14));
        }
        sheet.createFreezePane(1, 1);
        for (int c = 0; c < 15; c++) sheet.autoSizeColumn(c);
    }

    private void writeAggregateFieldCoverage(XSSFWorkbook wb, BatchSummary s, CellStyle header) {
        Sheet sheet = wb.createSheet("Aggregate Field Coverage");
        int row = 0;
        List<ProfileKey> profiles = profileSet.all();
        java.util.List<String> headers = new java.util.ArrayList<>();
        headers.add("Field#");
        headers.add("NUM_DATA");
        headers.add("FunDataXML path");
        for (ProfileKey p : profiles) headers.add(p.displayName());
        headers.add("Σ Present");
        headers.add("Σ Missing");
        headers.add("Σ Invalid");
        XlsxStyles.addRow(sheet, row++, header, headers.toArray(new String[0]));

        Map<String, long[]> byField = new HashMap<>();
        for (FieldSpec spec : catalog.fields()) byField.put(spec.numKey(), new long[3]);

        for (BatchResult r : s.results()) {
            if (r.status() != BatchFileStatus.OK || r.file() == null) continue;
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
                if (f.ruleId() != null && f.ruleId().startsWith("FORMAT/")) {
                    long[] arr = byField.get(f.fieldNum());
                    if (arr != null) arr[2]++;
                }
            }
        }
        for (FieldSpec spec : catalog.fields()) {
            long[] c = byField.get(spec.numKey());
            Row rr = sheet.createRow(row++);
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
        sheet.createFreezePane(2, 1);
        int totalCols = 3 + profiles.size() + 3;
        for (int c = 0; c < totalCols; c++) sheet.autoSizeColumn(c);
    }

    private void writePerFileScores(XSSFWorkbook wb, BatchSummary s,
                                    CellStyle header, CellStyle pct) {
        Sheet sheet = wb.createSheet("Per-File Scores");
        int row = 0;
        XlsxStyles.addRow(sheet, row++, header, "File", "Category", "Score");
        for (BatchResult r : s.results()) {
            if (r.status() != BatchFileStatus.OK || r.report() == null) continue;
            for (ScoreCategory cat : ScoreCategory.values()) {
                Double v = r.report().scores().get(cat);
                if (v == null) continue;
                Row rr = sheet.createRow(row++);
                rr.createCell(0).setCellValue(r.displayName());
                rr.createCell(1).setCellValue(cat.name());
                Cell c = rr.createCell(2);
                c.setCellValue(v);
                c.setCellStyle(pct);
            }
        }
        sheet.createFreezePane(0, 1);
        for (int c = 0; c < 3; c++) sheet.autoSizeColumn(c);
    }

    private void writeSkippedOrFailed(XSSFWorkbook wb, BatchSummary s, CellStyle header) {
        Sheet sheet = wb.createSheet("Skipped or Failed Files");
        int row = 0;
        XlsxStyles.addRow(sheet, row++, header, "File", "Status", "Error message");
        for (BatchResult r : s.results()) {
            if (r.status() == BatchFileStatus.OK) continue;
            Row rr = sheet.createRow(row++);
            rr.createCell(0).setCellValue(r.displayName());
            rr.createCell(1).setCellValue(r.status().name());
            rr.createCell(2).setCellValue(nz(r.errorMessage()));
        }
        sheet.createFreezePane(0, 1);
        for (int c = 0; c < 3; c++) sheet.autoSizeColumn(c);
    }

    private static Map<Severity, Long> bySeverity(List<Finding> findings) {
        Map<Severity, Long> m = new LinkedHashMap<>();
        for (Finding f : findings) m.merge(f.severity(), 1L, Long::sum);
        return m;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
