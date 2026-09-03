package com.findatex.validator.report;

import com.findatex.validator.domain.RawCell;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * UI-agnostic "annotated source" grid: the original upload mirrored cell-for-cell, joined with
 * the findings that point at each cell. Shared by the Excel sheet writer and the desktop
 * in-app view so both agree on where a finding lands and how it is described.
 *
 * <p>Column convention (same as the Excel sheet): mirror column {@code 0} is the synthetic
 * <em>Row</em> helper column carrying the logical row index and row-level findings; source
 * column {@code c} is mirror column {@code c + 1}. {@link Row#cells()} holds only the source
 * columns, so {@code cells().get(c)} is mirror column {@code c + 1}.</p>
 */
public final class AnnotatedSourceModel {

    static final int MAX_FINDING_MSG = 400;
    static final int MAX_COMMENT_TEXT = 1500;

    /** One source cell plus the findings attached to it; {@code severity} is the worst one or {@code null}. */
    public record Cell(SourceMirror.SourceCell source, Severity severity, List<Finding> findings) {
        public String text() { return source.asText(); }
        public boolean hasFindings() { return !findings.isEmpty(); }
    }

    /**
     * One mirror row. {@code logicalRow} is the parsed {@link TptRow#rowIndex()} or {@code null}
     * for rows the loader did not treat as data (header, title rows, trailing junk).
     * {@code rowSeverity} is the worst severity across the whole row, or {@code null}.
     */
    public record Row(int mirrorIndex, Integer logicalRow, boolean header, Severity rowSeverity,
                      List<Finding> rowLevelFindings, List<Cell> cells) {
        public boolean hasFindings() { return rowSeverity != null; }
    }

    /** Mirror coordinates; {@code mirrorCol == 0} is the Row helper column. */
    public record CellRef(int mirrorRow, int mirrorCol) {}

    private final List<Row> rows;
    private final int headerRowIndex;
    private final int width;
    private final List<String> headers;
    private final Set<Integer> columnsWithFindings;
    private final Map<Finding, CellRef> locations;

    private AnnotatedSourceModel(List<Row> rows, int headerRowIndex, int width, List<String> headers,
                                 Set<Integer> columnsWithFindings, Map<Finding, CellRef> locations) {
        this.rows = rows;
        this.headerRowIndex = headerRowIndex;
        this.width = width;
        this.headers = headers;
        this.columnsWithFindings = columnsWithFindings;
        this.locations = locations;
    }

    /**
     * Re-reads the original file behind {@code report.file()} and joins it with the findings.
     *
     * @throws IOException when the source can no longer be read (moved, deleted, never persisted)
     */
    public static AnnotatedSourceModel build(QualityReport report) throws IOException {
        SourceMirror.SourceData src = SourceMirror.read(report.file());

        Map<Integer, TptRow> rowsByLogical = new HashMap<>();
        Map<Integer, Integer> mirrorRowToLogical = new HashMap<>();
        for (TptRow tr : report.file().rows()) {
            rowsByLogical.put(tr.rowIndex(), tr);
            Iterator<RawCell> it = tr.all().values().iterator();
            if (it.hasNext()) {
                mirrorRowToLogical.put(it.next().sourceRow() - 1, tr.rowIndex());
            }
        }

        Map<CellRef, List<Finding>> byCell = new HashMap<>();
        Map<Integer, Severity> worstByRow = new HashMap<>();
        Map<Finding, CellRef> locations = new HashMap<>();
        for (Finding f : report.findings()) {
            CellRef ref = locate(f, rowsByLogical);
            if (ref == null) continue;
            locations.put(f, ref);
            byCell.computeIfAbsent(ref, k -> new ArrayList<>()).add(f);
            worstByRow.merge(ref.mirrorRow(), f.severity(), AnnotatedSourceModel::worse);
        }

        int width = 0;
        for (List<SourceMirror.SourceCell> row : src.rows()) width = Math.max(width, row.size());

        List<Row> rows = new ArrayList<>(src.rows().size());
        Set<Integer> colsWithFindings = new TreeSet<>();
        for (int r = 0; r < src.rows().size(); r++) {
            List<SourceMirror.SourceCell> srcRow = src.rows().get(r);
            List<Cell> cells = new ArrayList<>(width);
            for (int c = 0; c < width; c++) {
                SourceMirror.SourceCell sc = c < srcRow.size() ? srcRow.get(c) : SourceMirror.SourceCell.BLANK;
                List<Finding> fs = byCell.get(new CellRef(r, c + 1));
                if (fs == null) {
                    cells.add(new Cell(sc, null, List.of()));
                } else {
                    colsWithFindings.add(c + 1);
                    cells.add(new Cell(sc, worstSeverity(fs), List.copyOf(fs)));
                }
            }
            List<Finding> rowLevel = byCell.getOrDefault(new CellRef(r, 0), List.of());
            if (!rowLevel.isEmpty()) colsWithFindings.add(0);
            rows.add(new Row(r, mirrorRowToLogical.get(r), r == src.headerRowIndex(),
                    worstByRow.get(r), List.copyOf(rowLevel), Collections.unmodifiableList(cells)));
        }

        List<String> headers = new ArrayList<>(width);
        List<SourceMirror.SourceCell> headerRow = src.rows().isEmpty()
                ? List.of() : src.rows().get(src.headerRowIndex());
        for (int c = 0; c < width; c++) {
            headers.add(c < headerRow.size() ? headerRow.get(c).asText() : "");
        }

        return new AnnotatedSourceModel(Collections.unmodifiableList(rows), src.headerRowIndex(), width,
                Collections.unmodifiableList(headers), Collections.unmodifiableSet(colsWithFindings),
                locations);
    }

    /** All mirror rows in file order, including the header row and any rows above it. */
    public List<Row> rows() { return rows; }

    public int headerRowIndex() { return headerRowIndex; }

    /** Number of source columns (excluding the helper column). */
    public int width() { return width; }

    /** Header-row text per source column ({@code ""} when blank). */
    public List<String> headers() { return headers; }

    /** Mirror column indices that carry at least one finding ({@code 0} = row-level findings exist). */
    public Set<Integer> columnsWithFindings() { return columnsWithFindings; }

    /** Where a finding lands; empty for portfolio/global findings or rows that were not parsed. */
    public Optional<CellRef> locate(Finding f) {
        return Optional.ofNullable(locations.get(f));
    }

    private static CellRef locate(Finding f, Map<Integer, TptRow> rowsByLogical) {
        if (f.rowIndex() == null) return null;
        TptRow tr = rowsByLogical.get(f.rowIndex());
        if (tr == null) return null;
        Iterator<RawCell> it = tr.all().values().iterator();
        if (!it.hasNext()) return null;
        int mirrorRow = it.next().sourceRow() - 1;
        int mirrorCol = 0;
        if (f.fieldNum() != null) {
            RawCell rc = tr.all().get(f.fieldNum());
            mirrorCol = rc == null ? 0 : rc.sourceCol();   // RawCell.sourceCol is 1-based = mirror column
        }
        return new CellRef(mirrorRow, mirrorCol);
    }

    /**
     * Human-readable list of findings for one cell: ERROR → WARNING → INFO, then by rule id,
     * each as {@code [SEVERITY] ruleId — message}. Messages are capped at 400 characters and the
     * whole text at 1500 — the same text the Excel sheet puts into the cell comment.
     */
    public static String describe(List<Finding> findings) {
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

    static Severity worstSeverity(List<Finding> findings) {
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

    private static int severityOrder(Severity sev) {
        return switch (sev) {
            case ERROR -> 0;
            case WARNING -> 1;
            case INFO -> 2;
        };
    }
}
