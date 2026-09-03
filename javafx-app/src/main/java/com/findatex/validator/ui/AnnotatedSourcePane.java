package com.findatex.validator.ui;

import com.findatex.validator.report.AnnotatedSourceModel;
import com.findatex.validator.report.QualityReport;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * In-app counterpart of the Excel "Annotated Source" sheet: the original file cell-for-cell,
 * cells tinted by worst finding severity, a tooltip listing the findings, and a leading
 * <em>Row</em> helper column carrying the logical row index and row-level findings.
 *
 * <p>Lifecycle: {@link #setReport(QualityReport)} only remembers the report; the (POI-backed,
 * potentially slow) re-read of the source file runs on a background thread the first time the
 * pane is {@linkplain #setActive(boolean) active}, so users who never open the tab pay nothing.
 * {@link #showCell(Finding)} works before the grid is loaded — the jump is replayed once it is.</p>
 */
public final class AnnotatedSourcePane extends BorderPane {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedSourcePane.class);

    static final String PLACEHOLDER_IDLE = "Run a validation to see the original file here.";
    static final String PLACEHOLDER_LOADING = "Reading original file…";
    static final String PLACEHOLDER_UNAVAILABLE =
            "Original file no longer available — see the Findings tab for details.";
    static final String PLACEHOLDER_EMPTY = "Original file is empty.";

    private static final double ROW_COL_WIDTH = 60;
    private static final double SOURCE_COL_WIDTH = 120;

    private final String threadName;
    private final CheckBox onlyRowsWithFindings = new CheckBox("Only rows with findings");
    private final CheckBox onlyColumnsWithFindings = new CheckBox("Only columns with findings");
    private final Label countLabel = new Label();
    private final Label placeholder = new Label(PLACEHOLDER_IDLE);
    private final TableView<AnnotatedSourceModel.Row> table = new TableView<>();
    private final ObservableList<AnnotatedSourceModel.Row> rows = FXCollections.observableArrayList();
    private final FilteredList<AnnotatedSourceModel.Row> filtered = new FilteredList<>(rows, r -> true);
    /** Source-column TableColumns by 0-based source index (mirror column = index + 1). */
    private final List<TableColumn<AnnotatedSourceModel.Row, AnnotatedSourceModel.Row>> sourceColumns = new ArrayList<>();

    private QualityReport pendingReport;
    private QualityReport loadedReport;
    private AnnotatedSourceModel model;
    private Finding pendingJump;
    private boolean active;
    private boolean loading;

    /** @param threadName name for the background loader thread (e.g. {@code "TPT-annotated-source"}) */
    public AnnotatedSourcePane(String threadName) {
        this.threadName = threadName;

        HBox bar = new HBox(14);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 0, 8, 0));
        bar.getChildren().addAll(onlyRowsWithFindings, onlyColumnsWithFindings,
                legend("Error", Severity.ERROR), legend("Warning", Severity.WARNING),
                legend("Info", Severity.INFO), countLabel);
        setTop(bar);

        table.getStyleClass().add("source-grid");
        table.setItems(filtered);
        table.setPlaceholder(placeholder);
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        setCenter(table);

        onlyRowsWithFindings.selectedProperty().addListener((o, a, b) -> applyRowFilter());
        onlyColumnsWithFindings.selectedProperty().addListener((o, a, b) -> applyColumnFilter());
    }

    // ===== Public API used by TemplateTabController ===========================

    /** Remembers the report to show; loading happens lazily once the pane is active. */
    public void setReport(QualityReport report) {
        pendingReport = report;
        pendingJump = null;
        if (report != loadedReport) {
            resetGrid(PLACEHOLDER_IDLE);
        }
        ensureLoaded();
    }

    /** Drops everything (file selection changed to a failed file, batch mode toggled …). */
    public void clear() {
        pendingReport = null;
        pendingJump = null;
        resetGrid(PLACEHOLDER_IDLE);
    }

    /** Called when the hosting tab becomes (in)visible — triggers the deferred load. */
    public void setActive(boolean active) {
        this.active = active;
        ensureLoaded();
    }

    /** Scrolls to and selects the cell a finding points at; no-op for global findings. */
    public void showCell(Finding finding) {
        if (finding == null) return;
        if (model == null || loadedReport != pendingReport) {
            pendingJump = finding;
            ensureLoaded();
            return;
        }
        jumpTo(finding);
    }

    // ===== Loading ==============================================================

    private void ensureLoaded() {
        if (!active || loading || pendingReport == null || pendingReport == loadedReport) return;
        QualityReport target = pendingReport;
        loading = true;
        placeholder.setText(PLACEHOLDER_LOADING);
        Task<AnnotatedSourceModel> task = new Task<>() {
            @Override protected AnnotatedSourceModel call() throws Exception {
                return AnnotatedSourceModel.build(target);
            }
        };
        task.setOnSucceeded(e -> {
            loading = false;
            if (target != pendingReport) { ensureLoaded(); return; }   // superseded meanwhile
            loadedReport = target;
            render(task.getValue());
            if (pendingJump != null) {
                Finding f = pendingJump;
                pendingJump = null;
                jumpTo(f);
            }
        });
        task.setOnFailed(e -> {
            loading = false;
            log.warn("Could not build annotated source view: {}", String.valueOf(task.getException()));
            if (target != pendingReport) { ensureLoaded(); return; }
            loadedReport = target;
            model = null;
            resetGrid(PLACEHOLDER_UNAVAILABLE);
        });
        Thread t = new Thread(task, threadName);
        t.setDaemon(true);
        t.start();
    }

    private void resetGrid(String placeholderText) {
        model = null;
        loadedReport = null;
        rows.clear();
        table.getColumns().clear();
        sourceColumns.clear();
        countLabel.setText("");
        placeholder.setText(placeholderText);
    }

    private void render(AnnotatedSourceModel m) {
        model = m;
        table.getColumns().clear();
        sourceColumns.clear();
        if (m.rows().isEmpty()) {
            rows.clear();
            countLabel.setText("");
            placeholder.setText(PLACEHOLDER_EMPTY);
            return;
        }

        TableColumn<AnnotatedSourceModel.Row, AnnotatedSourceModel.Row> rowCol = new TableColumn<>("Row");
        rowCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        rowCol.setCellFactory(c -> new RowHelperCell());
        rowCol.setPrefWidth(ROW_COL_WIDTH);
        rowCol.setSortable(false);
        rowCol.setReorderable(false);
        List<TableColumn<AnnotatedSourceModel.Row, ?>> cols = new ArrayList<>(m.width() + 1);
        cols.add(rowCol);
        for (int c = 0; c < m.width(); c++) {
            final int sourceCol = c;
            TableColumn<AnnotatedSourceModel.Row, AnnotatedSourceModel.Row> col =
                    new TableColumn<>(AnnotatedSourceColumns.title(c, m.headers().get(c)));
            col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
            col.setCellFactory(x -> new SourceCell(sourceCol));
            col.setPrefWidth(SOURCE_COL_WIDTH);
            col.setSortable(false);
            col.setReorderable(false);
            sourceColumns.add(col);
            cols.add(col);
        }
        table.getColumns().setAll(cols);

        List<AnnotatedSourceModel.Row> data = m.rows().stream().filter(r -> !r.header()).toList();
        rows.setAll(data);
        long withFindings = data.stream().filter(AnnotatedSourceModel.Row::hasFindings).count();
        countLabel.setText(AnnotatedSourceColumns.summary(data.size(), m.width(), (int) withFindings));
        placeholder.setText(PLACEHOLDER_IDLE);
        applyRowFilter();
        applyColumnFilter();
    }

    // ===== Filters ==============================================================

    private void applyRowFilter() {
        boolean only = onlyRowsWithFindings.isSelected();
        filtered.setPredicate(r -> !only || r.hasFindings());
    }

    private void applyColumnFilter() {
        if (model == null) return;
        boolean only = onlyColumnsWithFindings.isSelected();
        for (int c = 0; c < sourceColumns.size(); c++) {
            sourceColumns.get(c).setVisible(!only || model.columnsWithFindings().contains(c + 1));
        }
    }

    // ===== Jump =================================================================

    private void jumpTo(Finding finding) {
        AnnotatedSourceModel.CellRef ref = model.locate(finding).orElse(null);
        if (ref == null) return;
        int idx = -1;
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).mirrorIndex() == ref.mirrorRow()) { idx = i; break; }
        }
        if (idx < 0) return;
        TableColumn<AnnotatedSourceModel.Row, ?> col = ref.mirrorCol() == 0
                ? table.getColumns().get(0)
                : sourceColumns.get(ref.mirrorCol() - 1);
        table.getSelectionModel().clearAndSelect(idx, col);
        table.scrollTo(idx);
        table.scrollToColumn(col);
        table.requestFocus();
    }

    // ===== Cells ================================================================

    private static Label legend(String text, Severity severity) {
        Label l = new Label(text);
        l.getStyleClass().addAll("source-legend", AnnotatedSourceColumns.styleClassFor(severity));
        return l;
    }

    private static Tooltip newTooltip() {
        Tooltip t = new Tooltip();
        t.setShowDelay(Duration.millis(250));
        t.setShowDuration(Duration.seconds(30));
        t.setWrapText(true);
        t.setMaxWidth(520);
        return t;
    }

    /** Base cell: tinted by severity, tooltip only when findings exist. */
    private abstract static class SeverityCell extends TableCell<AnnotatedSourceModel.Row, AnnotatedSourceModel.Row> {
        private Tooltip tooltip;

        void apply(String text, Severity severity, List<Finding> findings) {
            setText(text);
            getStyleClass().removeAll("source-cell-error", "source-cell-warn", "source-cell-info");
            String cls = AnnotatedSourceColumns.styleClassFor(severity);
            if (cls != null) getStyleClass().add(cls);
            if (findings != null && !findings.isEmpty()) {
                if (tooltip == null) tooltip = newTooltip();
                tooltip.setText(AnnotatedSourceModel.describe(findings));
                setTooltip(tooltip);
            } else {
                setTooltip(null);
            }
        }

        void reset() {
            setText(null);
            setTooltip(null);
            getStyleClass().removeAll("source-cell-error", "source-cell-warn", "source-cell-info");
        }
    }

    private static final class RowHelperCell extends SeverityCell {
        RowHelperCell() {
            getStyleClass().add("source-row-col");
            setMinWidth(Region.USE_PREF_SIZE);
        }

        @Override protected void updateItem(AnnotatedSourceModel.Row row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) { reset(); return; }
            apply(row.logicalRow() == null ? "" : Integer.toString(row.logicalRow()),
                    row.rowSeverity(), row.rowLevelFindings());
        }
    }

    private static final class SourceCell extends SeverityCell {
        private final int sourceCol;

        SourceCell(int sourceCol) { this.sourceCol = sourceCol; }

        @Override protected void updateItem(AnnotatedSourceModel.Row row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null || sourceCol >= row.cells().size()) { reset(); return; }
            AnnotatedSourceModel.Cell cell = row.cells().get(sourceCol);
            apply(cell.text(), cell.severity(), cell.findings());
        }
    }
}
