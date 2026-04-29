package com.findatex.validator.ui;

import com.findatex.validator.batch.BatchFileStatus;
import com.findatex.validator.batch.BatchResult;
import com.findatex.validator.batch.BatchSummary;
import com.findatex.validator.batch.BatchValidationOptions;
import com.findatex.validator.batch.BatchValidationService;
import com.findatex.validator.batch.FolderScanner;
import com.findatex.validator.config.AppSettings;
import com.findatex.validator.config.SettingsService;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.external.ExternalValidationService;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.report.CombinedXlsxReportWriter;
import com.findatex.validator.report.GenerationUi;
import com.findatex.validator.report.QualityReport;
import com.findatex.validator.report.QualityScorer;
import com.findatex.validator.report.ScoreCategory;
import com.findatex.validator.report.XlsxReportWriter;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateRuleSet;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.FindingEnricher;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationEngine;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Per-template controller — hosts one tab inside the {@link MainController} TabPane. Owns the
 * version selector, file/folder picker, dynamically built profile checkboxes, validate/export
 * controls, scores and the master-detail (files / findings) tables. Loads the template's spec
 * lazily on first validation.
 */
public final class TemplateTabController {

    private static final Logger log = LoggerFactory.getLogger(TemplateTabController.class);
    private static final int FLAT_FINDINGS_DISPLAY_CAP = 10_000;

    private final TemplateDefinition template;
    private TemplateVersion selectedVersion;
    private SpecCatalog catalog;             // lazily loaded; null if spec not installed
    private boolean specLoadFailed;
    private Stage stage;

    // --- Mode state ---------------------------------------------------------
    private boolean batchMode;               // false = single file, true = folder
    private Path batchFolder;
    private QualityReport currentReport;     // single-file mode
    private BatchSummary currentBatch;       // folder mode

    private final ObservableList<FindingRow> allFindings = FXCollections.observableArrayList();
    private FilteredList<FindingRow> filteredFindings;
    private final ObservableList<FileRow> fileRows = FXCollections.observableArrayList();
    private final Map<ProfileKey, CheckBox> profileCheckBoxes = new LinkedHashMap<>();

    // --- FXML wiring --------------------------------------------------------
    @FXML private ComboBox<TemplateVersion> versionCombo;
    @FXML private Label versionDateLabel;
    @FXML private Label emptySpecNotice;

    @FXML private TextField filePathField;
    @FXML private Button browseButton;
    @FXML private Button browseFolderButton;
    @FXML private Label batchModeLabel;
    @FXML private Button validateButton;
    @FXML private MenuButton exportMenu;
    @FXML private MenuItem exportPerFileItem;
    @FXML private MenuItem exportCombinedItem;
    @FXML private ProgressIndicator progress;
    @FXML private Label statusLabel;

    @FXML private FlowPane profilePane;
    @FXML private HBox externalRow;
    @FXML private CheckBox externalEnabled;
    @FXML private Label externalStatusLabel;

    @FXML private FlowPane scorePane;

    @FXML private TableView<FileRow> filesTable;
    @FXML private TableColumn<FileRow, String> colFileName;
    @FXML private TableColumn<FileRow, String> colFileStatus;
    @FXML private TableColumn<FileRow, String> colFileScore;
    @FXML private TableColumn<FileRow, String> colFileErrors;
    @FXML private TableColumn<FileRow, String> colFileWarnings;
    @FXML private TableColumn<FileRow, String> colFileInfos;
    @FXML private TableColumn<FileRow, String> colFileRows;
    @FXML private TableColumn<FileRow, String> colFileTime;

    @FXML private CheckBox filterErrors;
    @FXML private CheckBox filterWarnings;
    @FXML private CheckBox filterInfo;
    @FXML private Label findingCountLabel;
    @FXML private TableView<FindingRow> findingsTable;
    @FXML private TableColumn<FindingRow, String> colSeverity;
    @FXML private TableColumn<FindingRow, String> colProfile;
    @FXML private TableColumn<FindingRow, String> colFundId;
    @FXML private TableColumn<FindingRow, String> colFundName;
    @FXML private TableColumn<FindingRow, String> colDate;
    @FXML private TableColumn<FindingRow, String> colRow;
    @FXML private TableColumn<FindingRow, String> colInstCode;
    @FXML private TableColumn<FindingRow, String> colInstName;
    @FXML private TableColumn<FindingRow, String> colWeight;
    @FXML private TableColumn<FindingRow, String> colRule;
    @FXML private TableColumn<FindingRow, String> colField;
    @FXML private TableColumn<FindingRow, String> colFieldName;
    @FXML private TableColumn<FindingRow, String> colMessage;

    public TemplateTabController(TemplateDefinition template) {
        this.template = Objects.requireNonNull(template, "template");
        this.selectedVersion = template.latest();
    }

    public TemplateDefinition template() {
        return template;
    }

    public TemplateVersion selectedVersion() {
        return selectedVersion;
    }

    public void setSelectedVersion(TemplateVersion version) {
        if (!template.versions().contains(version)) {
            throw new IllegalArgumentException(
                    "Version " + version.version() + " is not registered for template " + template.id());
        }
        this.selectedVersion = version;
        this.catalog = null;          // force reload on next validation
        this.specLoadFailed = false;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * Builds the profile checkboxes for {@link #selectedVersion}. Called on init and on every
     * version change — EPT V2.0 publishes UCITS-KIID flags while V2.1 publishes UK flags, so
     * the checkbox set must follow the version. Single-profile templates (EMT) hide the pane
     * entirely since the lone checkbox would carry no information.
     */
    private void rebuildProfilePane() {
        profilePane.getChildren().clear();
        profileCheckBoxes.clear();
        var profiles = template.profilesFor(selectedVersion).all();
        for (ProfileKey p : profiles) {
            CheckBox cb = new CheckBox(p.displayName());
            cb.setSelected(true);
            profilePane.getChildren().add(cb);
            profileCheckBoxes.put(p, cb);
        }
        boolean hide = profiles.size() <= 1;
        profilePane.setVisible(!hide);
        profilePane.setManaged(!hide);
    }

    @FXML
    public void initialize() {
        // Version selector populated from the template definition.
        versionCombo.getItems().setAll(template.versions());
        versionCombo.setValue(selectedVersion);
        versionCombo.valueProperty().addListener((o, was, is) -> {
            if (is == null) return;
            setSelectedVersion(is);
            updateVersionDateLabel();
            rebuildProfilePane();   // EPT swaps its profile set between V2.0 and V2.1
        });
        updateVersionDateLabel();

        rebuildProfilePane();

        // External validation row — visible whenever the active template/version declares any
        // ISIN/LEI columns to look up. Templates without external config keep the row hidden.
        boolean externalSupported = !template.externalValidationConfigFor(selectedVersion).isEmpty();
        externalRow.setVisible(externalSupported);
        externalRow.setManaged(externalSupported);

        // Findings table columns.
        colSeverity.setCellValueFactory(new PropertyValueFactory<>("severity"));
        colProfile.setCellValueFactory(new PropertyValueFactory<>("profile"));
        colFundId.setCellValueFactory(new PropertyValueFactory<>("fundId"));
        colFundName.setCellValueFactory(new PropertyValueFactory<>("fundName"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("valuationDate"));
        colRow.setCellValueFactory(new PropertyValueFactory<>("rowIndex"));
        colInstCode.setCellValueFactory(new PropertyValueFactory<>("instrumentCode"));
        colInstName.setCellValueFactory(new PropertyValueFactory<>("instrumentName"));
        colWeight.setCellValueFactory(new PropertyValueFactory<>("weight"));
        colRule.setCellValueFactory(new PropertyValueFactory<>("rule"));
        colField.setCellValueFactory(new PropertyValueFactory<>("field"));
        colFieldName.setCellValueFactory(new PropertyValueFactory<>("fieldName"));
        colMessage.setCellValueFactory(new PropertyValueFactory<>("message"));

        filteredFindings = new FilteredList<>(allFindings, fr -> true);
        findingsTable.setItems(filteredFindings);

        filterErrors.selectedProperty().addListener((o, a, b) -> applyFilters());
        filterWarnings.selectedProperty().addListener((o, a, b) -> applyFilters());
        filterInfo.selectedProperty().addListener((o, a, b) -> applyFilters());

        filePathField.textProperty().addListener((o, a, b) ->
                validateButton.setDisable(b == null || b.trim().isEmpty()));

        // Files (master) table.
        colFileName.setCellValueFactory(new PropertyValueFactory<>("displayName"));
        colFileStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colFileScore.setCellValueFactory(new PropertyValueFactory<>("score"));
        colFileErrors.setCellValueFactory(new PropertyValueFactory<>("errors"));
        colFileWarnings.setCellValueFactory(new PropertyValueFactory<>("warnings"));
        colFileInfos.setCellValueFactory(new PropertyValueFactory<>("infos"));
        colFileRows.setCellValueFactory(new PropertyValueFactory<>("rows"));
        colFileTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        filesTable.setItems(fileRows);
        filesTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(FileRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("file-row-error");
                if (!empty && item != null && !"OK".equals(item.getStatus())) {
                    getStyleClass().add("file-row-error");
                }
            }
        });
        filesTable.getSelectionModel().selectedItemProperty().addListener((o, was, is) -> onFileRowSelected(is));

        if (externalSupported) {
            AppSettings cur = SettingsService.getInstance().getCurrent();
            externalEnabled.setSelected(cur.external().enabled());
            externalEnabled.selectedProperty().addListener((o, was, is) ->
                    SettingsService.getInstance().update(
                            SettingsService.getInstance().getCurrent().withExternalEnabled(is)));
        }

        // Combined export only makes sense in batch mode; per-file works in both.
        exportCombinedItem.setDisable(true);
    }

    private void updateVersionDateLabel() {
        if (versionDateLabel == null) return;
        if (selectedVersion.releaseDate() != null) {
            versionDateLabel.setText("(" + selectedVersion.releaseDate() + ")");
        } else {
            versionDateLabel.setText("");
        }
    }

    private SpecCatalog catalog() {
        if (catalog != null) return catalog;
        if (specLoadFailed) return null;
        try {
            catalog = template.specLoaderFor(selectedVersion).load();
            emptySpecNotice.setManaged(false);
            emptySpecNotice.setVisible(false);
            return catalog;
        } catch (Exception ex) {
            log.warn("Spec for {} {} could not be loaded: {}", template.id(), selectedVersion.version(), ex.getMessage());
            specLoadFailed = true;
            emptySpecNotice.setManaged(true);
            emptySpecNotice.setVisible(true);
            return null;
        }
    }

    @FXML
    private void onBrowse(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open " + template.displayName() + " file");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(template.displayName() + " (*.xlsx, *.csv)",
                        "*.xlsx", "*.xlsm", "*.csv", "*.tsv"),
                new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xlsm"),
                new FileChooser.ExtensionFilter("CSV", "*.csv", "*.tsv"));
        File chosen = fc.showOpenDialog(stage);
        if (chosen == null) return;
        leaveBatchMode();
        filePathField.setText(chosen.getAbsolutePath());
    }

    @FXML
    private void onBrowseFolder(ActionEvent e) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Open folder of " + template.displayName() + " files");
        File chosen = dc.showDialog(stage);
        if (chosen == null) return;
        enterBatchMode(chosen.toPath());
    }

    private void enterBatchMode(Path folder) {
        batchMode = true;
        batchFolder = folder;
        currentReport = null;
        currentBatch = null;
        fileRows.clear();
        allFindings.clear();
        scorePane.getChildren().clear();
        filePathField.setText(folder.toString());
        batchModeLabel.setText("Batch-Modus: alle unterstützten Dateien in '" + folder.getFileName() + "'");
        batchModeLabel.setManaged(true);
        batchModeLabel.setVisible(true);
        validateButton.setDisable(false);
        exportMenu.setDisable(true);
        statusLabel.setText("");
    }

    private void leaveBatchMode() {
        batchMode = false;
        batchFolder = null;
        currentBatch = null;
        fileRows.clear();
        batchModeLabel.setText("");
        batchModeLabel.setManaged(false);
        batchModeLabel.setVisible(false);
        exportCombinedItem.setDisable(true);
    }

    @FXML
    private void onValidate(ActionEvent e) {
        SpecCatalog cat = catalog();
        if (cat == null) {
            statusLabel.setText("Spec not installed");
            return;
        }
        Set<ProfileKey> profiles = collectActiveProfiles();
        if (profiles.isEmpty()) {
            statusLabel.setText("Select at least one profile");
            return;
        }
        AppSettings settings = SettingsService.getInstance().getCurrent();
        externalStatusLabel.setText("");

        if (batchMode) {
            onValidateBatch(cat, settings, profiles);
        } else {
            onValidateSingle(cat, settings, profiles);
        }
    }

    private Set<ProfileKey> collectActiveProfiles() {
        Set<ProfileKey> profiles = new HashSet<>();
        for (Map.Entry<ProfileKey, CheckBox> entry : profileCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) profiles.add(entry.getKey());
        }
        return profiles;
    }

    // ===== Single-file validation ===========================================

    private void onValidateSingle(SpecCatalog cat, AppSettings settings, Set<ProfileKey> profiles) {
        String pathStr = filePathField.getText();
        if (pathStr == null || pathStr.isBlank()) return;
        Path path = Path.of(pathStr.trim());

        progress.setVisible(true);
        validateButton.setDisable(true);
        exportMenu.setDisable(true);
        statusLabel.setText("Loading and validating " + path.getFileName() + " ...");

        ExternalValidationConfig externalConfig = template.externalValidationConfigFor(selectedVersion);
        boolean externalSupported = !externalConfig.isEmpty();

        Stage progressStage = null;
        LookupProgressController progressController = null;
        if (externalSupported && settings.external().enabled()) {
            try {
                FXMLLoader pl = new FXMLLoader(getClass().getResource("/fxml/LookupProgressDialog.fxml"));
                Parent pRoot = pl.load();
                progressController = pl.getController();
                progressStage = new Stage();
                progressController.setStage(progressStage);
                progressStage.initModality(Modality.APPLICATION_MODAL);
                progressStage.setTitle("External validation");
                progressStage.setScene(new Scene(pRoot));
            } catch (Exception ex) {
                log.warn("Could not load progress dialog: {}", ex.getMessage());
            }
        }
        final Stage pStage = progressStage;
        final LookupProgressController pCtrl = progressController;

        Task<QualityReport> task = new Task<>() {
            @Override
            protected QualityReport call() throws Exception {
                TptFile file = new TptFileLoader(cat).load(path);
                TemplateRuleSet ruleSet = template.ruleSetFor(selectedVersion);
                List<Finding> findings = new ValidationEngine(cat, ruleSet).validate(file, profiles);

                if (externalSupported && settings.external().enabled()) {
                    Path cacheDir = resolveExternalCacheDir(settings);
                    ExternalValidationService svc = ExternalValidationService.forProduction(
                            cacheDir, settings.external().isin());
                    BooleanSupplier cancelled = pCtrl != null ? pCtrl::isCancelled : () -> false;
                    ExternalValidationService.ProgressSink sink = buildSink(pCtrl);
                    List<Finding> online = FindingEnricher.enrich(
                            file, svc.run(file, externalConfig, settings, cancelled, sink));
                    List<Finding> all = new ArrayList<>(findings);
                    all.addAll(online);
                    findings = all;
                }

                return new QualityScorer(cat).score(file, profiles, findings);
            }
        };
        task.setOnSucceeded(ev -> {
            if (pCtrl != null) pCtrl.close();
            QualityReport report = task.getValue();
            currentReport = report;
            // Single-file mode: master table shows just this file as a one-row summary.
            fileRows.setAll(FileRow.fromSingleReport(path, report));
            filesTable.getSelectionModel().select(0);
            renderReport(report);
            long extIssues = report.findings().stream()
                    .filter(f -> f.ruleId().startsWith("EXTERNAL/")).count();
            if (extIssues > 0) {
                externalStatusLabel.setText("⚠ Online validation: " + extIssues
                        + " issue(s) — see findings tab");
            }
            progress.setVisible(false);
            validateButton.setDisable(false);
            exportMenu.setDisable(false);
            exportCombinedItem.setDisable(true);   // not meaningful for one file
            statusLabel.setText("Validation complete: " + report.findings().size()
                    + " finding(s), " + report.file().rows().size() + " row(s).");
        });
        task.setOnFailed(ev -> {
            if (pCtrl != null) pCtrl.close();
            Throwable th = task.getException();
            log.error("Validation failed", th);
            progress.setVisible(false);
            validateButton.setDisable(false);
            statusLabel.setText("Validation failed: " + (th == null ? "" : th.getMessage()));
            new Alert(Alert.AlertType.ERROR,
                    "Validation failed:\n" + (th == null ? "(unknown)" : th.getMessage())).showAndWait();
        });
        new Thread(task, template.id() + "-validate").start();
        if (pStage != null) pStage.show();
    }

    // ===== Folder (batch) validation ========================================

    private void onValidateBatch(SpecCatalog cat, AppSettings settings, Set<ProfileKey> profiles) {
        if (batchFolder == null) {
            statusLabel.setText("No folder selected");
            return;
        }
        FolderScanner.ScanResult scan;
        try {
            scan = new FolderScanner().scan(batchFolder);
        } catch (Exception ex) {
            statusLabel.setText("Folder scan failed: " + ex.getMessage());
            return;
        }
        if (scan.accepted().isEmpty()) {
            statusLabel.setText("No supported files in '" + batchFolder.getFileName() + "'");
            return;
        }

        progress.setVisible(true);
        validateButton.setDisable(true);
        exportMenu.setDisable(true);
        fileRows.clear();
        allFindings.clear();
        scorePane.getChildren().clear();
        statusLabel.setText("Validating " + scan.accepted().size() + " file(s) in '"
                + batchFolder.getFileName() + "'...");

        ExternalValidationConfig externalConfig = template.externalValidationConfigFor(selectedVersion);
        boolean externalActive = !externalConfig.isEmpty() && settings.external().enabled();

        Stage progressStage = null;
        LookupProgressController progressController = null;
        try {
            FXMLLoader pl = new FXMLLoader(getClass().getResource("/fxml/LookupProgressDialog.fxml"));
            Parent pRoot = pl.load();
            progressController = pl.getController();
            progressStage = new Stage();
            progressController.setStage(progressStage);
            progressStage.initModality(Modality.APPLICATION_MODAL);
            progressStage.setTitle("Folder validation");
            progressStage.setScene(new Scene(pRoot));
            progressController.setBatchMode(true);
        } catch (Exception ex) {
            log.warn("Could not load progress dialog: {}", ex.getMessage());
        }
        final Stage pStage = progressStage;
        final LookupProgressController pCtrl = progressController;

        Path cacheDir = resolveExternalCacheDir(settings);
        BatchValidationOptions opts = new BatchValidationOptions(
                template, selectedVersion, profiles,
                externalActive,
                settings,
                cacheDir);

        Task<BatchSummary> task = new Task<>() {
            @Override
            protected BatchSummary call() {
                BatchValidationService svc = new BatchValidationService(cat, opts);
                BooleanSupplier cancelled = pCtrl != null ? pCtrl::isCancelled : () -> false;
                ExternalValidationService.ProgressSink sink = buildSink(pCtrl);
                return svc.run(scan.accepted(), cancelled, new BatchValidationService.Listener() {
                    @Override public void onProgress(com.findatex.validator.batch.BatchProgress p) {
                        if (pCtrl != null) pCtrl.updateBatch(p);
                    }
                    @Override public void onFileComplete(BatchResult r) {
                        Platform.runLater(() -> fileRows.add(FileRow.of(r)));
                    }
                }, sink);
            }
        };
        task.setOnSucceeded(ev -> {
            if (pCtrl != null) pCtrl.close();
            BatchSummary summary = task.getValue();
            currentBatch = summary;
            currentReport = null;
            progress.setVisible(false);
            validateButton.setDisable(false);
            exportMenu.setDisable(summary.results().stream().noneMatch(r -> r.status() == BatchFileStatus.OK));
            exportCombinedItem.setDisable(false);
            renderBatchHeader(summary);
            // Auto-select first OK row to populate the detail panes; leaving the table
            // selection-less also leaves scorePane and findings empty, which is confusing.
            int firstOk = -1;
            for (int i = 0; i < fileRows.size(); i++) {
                if ("OK".equals(fileRows.get(i).getStatus())) { firstOk = i; break; }
            }
            if (firstOk >= 0) filesTable.getSelectionModel().select(firstOk);
        });
        task.setOnFailed(ev -> {
            if (pCtrl != null) pCtrl.close();
            Throwable th = task.getException();
            log.error("Batch validation failed", th);
            progress.setVisible(false);
            validateButton.setDisable(false);
            statusLabel.setText("Batch validation failed: " + (th == null ? "" : th.getMessage()));
            new Alert(Alert.AlertType.ERROR,
                    "Batch validation failed:\n" + (th == null ? "(unknown)" : th.getMessage()))
                    .showAndWait();
        });
        new Thread(task, template.id() + "-batch").start();
        if (pStage != null) pStage.show();
    }

    private void renderBatchHeader(BatchSummary summary) {
        long ok = summary.countWithStatus(BatchFileStatus.OK);
        long failed = summary.countWithStatus(BatchFileStatus.LOAD_ERROR)
                + summary.countWithStatus(BatchFileStatus.VALIDATION_ERROR);
        StringBuilder sb = new StringBuilder();
        if (summary.cancelled()) sb.append("Cancelled — ");
        sb.append(ok).append(" file(s) validated");
        if (failed > 0) sb.append(", ").append(failed).append(" failed");
        sb.append(" in ").append(batchFolder.getFileName());
        if (summary.aggregateOverallScore().isPresent()) {
            double pct = summary.aggregateOverallScore().getAsDouble() * 100.0;
            sb.append(String.format(Locale.ROOT, " (Ø OVERALL %.1f%%)", pct));
        }
        statusLabel.setText(sb.toString());
    }

    // ===== Export ===========================================================

    @FXML
    private void onExportPerFile(ActionEvent e) {
        SpecCatalog cat = catalog();
        if (cat == null) return;

        if (batchMode && currentBatch != null) {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Export per-file reports to folder");
            File folder = dc.showDialog(stage);
            if (folder == null) return;
            Path target = folder.toPath();
            List<BatchResult> okResults = currentBatch.results().stream()
                    .filter(r -> r.status() == BatchFileStatus.OK).toList();
            int total = okResults.size();
            int failed = currentBatch.results().size() - total;
            statusLabel.setText("Exporting " + total + " report(s)...");
            Task<Void> task = new Task<>() {
                @Override protected Void call() throws Exception {
                    XlsxReportWriter writer = new XlsxReportWriter(cat,
                            template.profilesFor(selectedVersion),
                            selectedVersion, GenerationUi.DESKTOP);
                    for (BatchResult r : okResults) {
                        if (r.report() == null) continue;
                        Path out = target.resolve(reportFileNameFor(r.displayName()));
                        writer.write(r.report(), out);
                    }
                    return null;
                }
            };
            task.setOnSucceeded(ev -> statusLabel.setText("Wrote " + total + " report(s)"
                    + (failed > 0 ? " (skipped " + failed + " failed file(s))" : "")
                    + " to " + target.getFileName()));
            task.setOnFailed(ev -> reportExportFailure(task.getException()));
            new Thread(task, template.id() + "-export-batch").start();
        } else if (currentReport != null) {
            FileChooser fc = new FileChooser();
            fc.setTitle("Export Excel report");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel workbook", "*.xlsx"));
            String stem = currentReport.file().source().getFileName().toString().replaceFirst("\\.[^.]+$", "");
            fc.setInitialFileName(stem + ".report.xlsx");
            File out = fc.showSaveDialog(stage);
            if (out == null) return;
            Path target = out.toPath();
            Task<Void> task = new Task<>() {
                @Override protected Void call() throws Exception {
                    new XlsxReportWriter(cat,
                            template.profilesFor(selectedVersion),
                            selectedVersion, GenerationUi.DESKTOP)
                            .write(currentReport, target);
                    return null;
                }
            };
            task.setOnSucceeded(ev -> statusLabel.setText("Report exported: " + target.getFileName()));
            task.setOnFailed(ev -> reportExportFailure(task.getException()));
            new Thread(task, template.id() + "-export").start();
        }
    }

    @FXML
    private void onExportCombined(ActionEvent e) {
        SpecCatalog cat = catalog();
        if (cat == null || currentBatch == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export combined report");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel workbook", "*.xlsx"));
        String defaultName = batchFolder == null ? "combined.report.xlsx"
                : batchFolder.getFileName() + ".combined.report.xlsx";
        fc.setInitialFileName(defaultName);
        File out = fc.showSaveDialog(stage);
        if (out == null) return;
        Path target = out.toPath();
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                new CombinedXlsxReportWriter(cat,
                        template.profilesFor(selectedVersion),
                        selectedVersion, GenerationUi.DESKTOP)
                        .write(currentBatch, target);
                return null;
            }
        };
        task.setOnSucceeded(ev -> statusLabel.setText("Combined report exported: " + target.getFileName()));
        task.setOnFailed(ev -> reportExportFailure(task.getException()));
        new Thread(task, template.id() + "-export-combined").start();
    }

    private void reportExportFailure(Throwable th) {
        log.error("Export failed", th);
        statusLabel.setText("Export failed: " + (th == null ? "" : th.getMessage()));
        new Alert(Alert.AlertType.ERROR,
                "Export failed:\n" + (th == null ? "(unknown)" : th.getMessage())).showAndWait();
    }

    private static String reportFileNameFor(String sourceName) {
        return sourceName.replaceFirst("\\.[^.]+$", "") + ".report.xlsx";
    }

    private static Path resolveExternalCacheDir(AppSettings settings) {
        return settings.external().cache().directory().isEmpty()
                ? Path.of(System.getProperty("user.home"), ".config", "findatex-validator", "cache")
                : Path.of(settings.external().cache().directory());
    }

    // ===== Selection-driven detail rendering =================================

    private void onFileRowSelected(FileRow row) {
        if (row == null) return;
        if (row.batchResult != null) {
            BatchResult r = row.batchResult;
            if (r.status() == BatchFileStatus.OK && r.report() != null) {
                renderReport(r.report());
                statusLabel.setText("Showing " + r.displayName());
            } else {
                scorePane.getChildren().clear();
                allFindings.clear();
                applyFilters();
                statusLabel.setText("Could not validate " + r.displayName()
                        + ": " + (r.errorMessage() == null ? r.status().name() : r.errorMessage()));
            }
        } else if (row.singleReport != null) {
            renderReport(row.singleReport);
        }
    }

    // ===== External progress sink shared by single-file + batch ==============

    private static ExternalValidationService.ProgressSink buildSink(LookupProgressController pCtrl) {
        if (pCtrl == null) return ExternalValidationService.ProgressSink.NOOP;
        int[] leiState = {0, 0};
        int[] isinState = {0, 0};
        int[] cache = {0, 0};
        return new ExternalValidationService.ProgressSink() {
            @Override public void leiTotal(int total) {
                leiState[1] = total;
                pCtrl.update(leiState[0], leiState[1], isinState[0], isinState[1], cache[0], cache[1]);
            }
            @Override public void leiDone(int done) {
                leiState[0] = done;
                pCtrl.update(leiState[0], leiState[1], isinState[0], isinState[1], cache[0], cache[1]);
            }
            @Override public void isinTotal(int total) {
                isinState[1] = total;
                pCtrl.update(leiState[0], leiState[1], isinState[0], isinState[1], cache[0], cache[1]);
            }
            @Override public void isinDone(int done) {
                isinState[0] = done;
                pCtrl.update(leiState[0], leiState[1], isinState[0], isinState[1], cache[0], cache[1]);
            }
            @Override public void cacheStats(int hits, int total) {
                cache[0] += hits;
                cache[1] += total;
                pCtrl.update(leiState[0], leiState[1], isinState[0], isinState[1], cache[0], cache[1]);
            }
        };
    }

    private void renderReport(QualityReport report) {
        scorePane.getChildren().clear();
        Map<ScoreCategory, Double> scores = report.scores();
        for (ScoreCategory cat : ScoreCategory.values()) {
            Double v = scores.get(cat);
            if (v == null) continue;
            scorePane.getChildren().add(buildScoreCard(cat.name(), v));
        }
        for (Map.Entry<ProfileKey, Map<ScoreCategory, Double>> pe : report.perProfileScores().entrySet()) {
            Double v = pe.getValue().get(ScoreCategory.PROFILE_COMPLETENESS);
            if (v == null) continue;
            scorePane.getChildren().add(buildScoreCard(pe.getKey().displayName(), v));
        }

        List<FindingRow> rows = report.findings().stream().map(FindingRow::of).toList();
        if (rows.size() > FLAT_FINDINGS_DISPLAY_CAP) {
            rows = rows.subList(0, FLAT_FINDINGS_DISPLAY_CAP);
        }
        allFindings.setAll(rows);
        applyFilters();
    }

    private VBox buildScoreCard(String title, double value) {
        VBox card = new VBox();
        card.getStyleClass().add("score-card");
        Label t = new Label(title.replace('_', ' '));
        t.getStyleClass().add("title");
        Label v = new Label(String.format("%.1f %%", value * 100));
        v.getStyleClass().add("value");
        if (value >= 0.9)      v.getStyleClass().add("score-good");
        else if (value >= 0.7) v.getStyleClass().add("score-warn");
        else                   v.getStyleClass().add("score-poor");
        card.getChildren().addAll(t, v);
        return card;
    }

    private void applyFilters() {
        boolean showE = filterErrors.isSelected();
        boolean showW = filterWarnings.isSelected();
        boolean showI = filterInfo.isSelected();
        filteredFindings.setPredicate(fr -> {
            String s = fr.getSeverity();
            if ("ERROR".equals(s))   return showE;
            if ("WARNING".equals(s)) return showW;
            if ("INFO".equals(s))    return showI;
            return true;
        });
        if (findingCountLabel != null) {
            findingCountLabel.setText(filteredFindings.size() + " of " + allFindings.size() + " shown");
        }
    }

    /** Master row in the files TableView. Backed either by a single-file QualityReport or by a BatchResult. */
    public static final class FileRow {
        private final String displayName;
        private final String status;
        private final String score;
        private final String errors;
        private final String warnings;
        private final String infos;
        private final String rows;
        private final String time;
        final BatchResult batchResult;
        final QualityReport singleReport;

        private FileRow(String displayName, String status, String score, String errors,
                        String warnings, String infos, String rows, String time,
                        BatchResult batchResult, QualityReport singleReport) {
            this.displayName = displayName;
            this.status = status;
            this.score = score;
            this.errors = errors;
            this.warnings = warnings;
            this.infos = infos;
            this.rows = rows;
            this.time = time;
            this.batchResult = batchResult;
            this.singleReport = singleReport;
        }

        public static FileRow of(BatchResult r) {
            String score = "—";
            String rows = "—";
            String errors = "—";
            String warnings = "—";
            String infos = "—";
            if (r.status() == BatchFileStatus.OK && r.report() != null) {
                Double overall = r.report().scores().get(ScoreCategory.OVERALL);
                score = overall == null ? "—" : String.format(Locale.ROOT, "%.0f%%", overall * 100);
                rows = Integer.toString(r.file() == null ? 0 : r.file().rows().size());
                long e = r.findings().stream().filter(f -> f.severity() == Severity.ERROR).count();
                long w = r.findings().stream().filter(f -> f.severity() == Severity.WARNING).count();
                long i = r.findings().stream().filter(f -> f.severity() == Severity.INFO).count();
                errors = Long.toString(e);
                warnings = Long.toString(w);
                infos = Long.toString(i);
            }
            return new FileRow(r.displayName(), r.status().name(), score, errors, warnings, infos,
                    rows, formatMillis(r.elapsed().toMillis()), r, null);
        }

        public static List<FileRow> fromSingleReport(Path path, QualityReport report) {
            String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            Double overall = report.scores().get(ScoreCategory.OVERALL);
            String score = overall == null ? "—" : String.format(Locale.ROOT, "%.0f%%", overall * 100);
            long e = report.findings().stream().filter(f -> f.severity() == Severity.ERROR).count();
            long w = report.findings().stream().filter(f -> f.severity() == Severity.WARNING).count();
            long i = report.findings().stream().filter(f -> f.severity() == Severity.INFO).count();
            return List.of(new FileRow(name, "OK", score,
                    Long.toString(e), Long.toString(w), Long.toString(i),
                    Integer.toString(report.file().rows().size()),
                    "—", null, report));
        }

        private static String formatMillis(long millis) {
            if (millis < 1000) return millis + " ms";
            return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
        }

        public String getDisplayName() { return displayName; }
        public String getStatus()      { return status; }
        public String getScore()       { return score; }
        public String getErrors()      { return errors; }
        public String getWarnings()    { return warnings; }
        public String getInfos()       { return infos; }
        public String getRows()        { return rows; }
        public String getTime()        { return time; }
    }

    /** Row model for the findings TableView (must use String getters for PropertyValueFactory). */
    public static final class FindingRow {
        private final String severity;
        private final String profile;
        private final String fundId;
        private final String fundName;
        private final String valuationDate;
        private final String rowIndex;
        private final String instrumentCode;
        private final String instrumentName;
        private final String weight;
        private final String rule;
        private final String field;
        private final String fieldName;
        private final String message;

        private FindingRow(Finding f) {
            com.findatex.validator.validation.FindingContext c =
                    f.context() == null ? com.findatex.validator.validation.FindingContext.EMPTY : f.context();
            this.severity       = f.severity().name();
            this.profile        = f.profile() == null ? "" : f.profile().displayName();
            this.fundId         = nz(c.portfolioId());
            this.fundName       = nz(c.portfolioName());
            this.valuationDate  = nz(c.valuationDate());
            this.rowIndex       = f.rowIndex() == null ? "" : Integer.toString(f.rowIndex());
            this.instrumentCode = nz(c.instrumentCode());
            this.instrumentName = nz(c.instrumentName());
            this.weight         = formatWeight(c.valuationWeight());
            this.rule           = f.ruleId();
            this.field          = nz(f.fieldNum());
            this.fieldName      = nz(f.fieldName());
            this.message        = nz(f.message());
        }

        private static String nz(String s) { return s == null ? "" : s; }

        private static String formatWeight(String raw) {
            if (raw == null || raw.isBlank()) return "";
            try {
                double d = Double.parseDouble(raw.replace(",", "."));
                return String.format("%.2f %%", d * 100);
            } catch (NumberFormatException e) {
                return raw;
            }
        }

        public static FindingRow of(Finding f) { return new FindingRow(f); }
        public String getSeverity()       { return severity; }
        public String getProfile()        { return profile; }
        public String getFundId()         { return fundId; }
        public String getFundName()       { return fundName; }
        public String getValuationDate()  { return valuationDate; }
        public String getRowIndex()       { return rowIndex; }
        public String getInstrumentCode() { return instrumentCode; }
        public String getInstrumentName() { return instrumentName; }
        public String getWeight()         { return weight; }
        public String getRule()           { return rule; }
        public String getField()          { return field; }
        public String getFieldName()      { return fieldName; }
        public String getMessage()        { return message; }
    }
}
