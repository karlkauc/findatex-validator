package com.tpt.validator.ui;

import com.tpt.validator.config.AppSettings;
import com.tpt.validator.config.PasswordCipher;
import com.tpt.validator.config.SettingsService;
import com.tpt.validator.domain.TptFile;
import com.tpt.validator.external.proxy.ProxyConfig;
import com.tpt.validator.external.proxy.ProxyService;
import com.tpt.validator.ingest.TptFileLoader;
import com.tpt.validator.report.QualityReport;
import com.tpt.validator.report.QualityScorer;
import com.tpt.validator.report.ScoreCategory;
import com.tpt.validator.report.XlsxReportWriter;
import com.tpt.validator.spec.Profile;
import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.ValidationEngine;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.tpt.validator.external.ExternalValidationService;
import javafx.scene.Scene;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private final SpecCatalog catalog;
    private Stage stage;
    private QualityReport currentReport;

    private final ObservableList<FindingRow> allFindings = FXCollections.observableArrayList();
    private FilteredList<FindingRow> filteredFindings;

    @FXML private TextField filePathField;
    @FXML private Button browseButton;
    @FXML private Button validateButton;
    @FXML private Button exportButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label statusLabel;

    @FXML private CheckBox profileSolvencyII;
    @FXML private CheckBox profileIorpEiopa;
    @FXML private CheckBox profileNw675;
    @FXML private CheckBox profileSst;

    @FXML private CheckBox externalEnabled;
    @FXML private Button settingsButton;
    @FXML private Label externalStatusLabel;

    @FXML private FlowPane scorePane;

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

    public MainController(SpecCatalog catalog) {
        this.catalog = catalog;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
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

        AppSettings cur = SettingsService.getInstance().getCurrent();
        externalEnabled.setSelected(cur.external().enabled());
        externalEnabled.selectedProperty().addListener((o, was, is) ->
                SettingsService.getInstance().update(
                        SettingsService.getInstance().getCurrent().withExternalEnabled(is)));
    }

    @FXML
    private void onBrowse(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open TPT V7 file");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("TPT V7 (*.xlsx, *.csv)", "*.xlsx", "*.xlsm", "*.csv", "*.tsv"),
                new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xlsm"),
                new FileChooser.ExtensionFilter("CSV", "*.csv", "*.tsv"));
        File chosen = fc.showOpenDialog(stage);
        if (chosen != null) filePathField.setText(chosen.getAbsolutePath());
    }

    @FXML
    private void onValidate(ActionEvent e) {
        String pathStr = filePathField.getText();
        if (pathStr == null || pathStr.isBlank()) return;
        Path path = Path.of(pathStr.trim());

        Set<Profile> profiles = new HashSet<>();
        if (profileSolvencyII.isSelected()) profiles.add(Profile.SOLVENCY_II);
        if (profileIorpEiopa.isSelected())  profiles.add(Profile.IORP_EIOPA_ECB);
        if (profileNw675.isSelected())      profiles.add(Profile.NW_675);
        if (profileSst.isSelected())        profiles.add(Profile.SST);
        if (profiles.isEmpty()) {
            statusLabel.setText("Select at least one profile");
            return;
        }

        AppSettings settings = SettingsService.getInstance().getCurrent();
        externalStatusLabel.setText("");

        progress.setVisible(true);
        validateButton.setDisable(true);
        exportButton.setDisable(true);
        statusLabel.setText("Loading and validating " + path.getFileName() + " ...");

        // Optional progress dialog — only loaded when external is on.
        Stage progressStage = null;
        LookupProgressController progressController = null;
        if (settings.external().enabled()) {
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
                TptFile file = new TptFileLoader(catalog).load(path);
                List<Finding> findings = new ValidationEngine(catalog).validate(file, profiles);

                if (settings.external().enabled()) {
                    Path cacheDir = settings.external().cache().directory().isEmpty()
                            ? Path.of(System.getProperty("user.home"),
                                      ".config", "tpt-validator", "cache")
                            : Path.of(settings.external().cache().directory());
                    ExternalValidationService svc = ExternalValidationService.forProduction(
                            cacheDir, settings.external().isin());
                    BooleanSupplier cancelled = pCtrl != null ? pCtrl::isCancelled : () -> false;

                    int[] leiState = {0, 0};   // [done, total]
                    int[] isinState = {0, 0};
                    int[] cache = {0, 0};      // [hits, total]
                    ExternalValidationService.ProgressSink sink = new ExternalValidationService.ProgressSink() {
                        @Override public void leiTotal(int total) {
                            leiState[1] = total;
                            if (pCtrl != null) pCtrl.update(leiState[0], leiState[1], isinState[0], isinState[1], cache[0], cache[1]);
                        }
                        @Override public void leiDone(int done) {
                            leiState[0] = done;
                            if (pCtrl != null) pCtrl.update(leiState[0], leiState[1], isinState[0], isinState[1], cache[0], cache[1]);
                        }
                        @Override public void isinTotal(int total) {
                            isinState[1] = total;
                            if (pCtrl != null) pCtrl.update(leiState[0], leiState[1], isinState[0], isinState[1], cache[0], cache[1]);
                        }
                        @Override public void isinDone(int done) {
                            isinState[0] = done;
                            if (pCtrl != null) pCtrl.update(leiState[0], leiState[1], isinState[0], isinState[1], cache[0], cache[1]);
                        }
                        @Override public void cacheStats(int hits, int total) {
                            cache[0] += hits;   // accumulate across LEI + ISIN phases
                            cache[1] += total;
                            if (pCtrl != null) pCtrl.update(leiState[0], leiState[1], isinState[0], isinState[1], cache[0], cache[1]);
                        }
                    };

                    List<Finding> online = svc.run(file, settings, cancelled, sink);
                    List<Finding> all = new ArrayList<>(findings);
                    all.addAll(online);
                    findings = all;
                }

                return new QualityScorer(catalog).score(file, profiles, findings);
            }
        };
        task.setOnSucceeded(ev -> {
            if (pCtrl != null) pCtrl.close();
            QualityReport report = task.getValue();
            currentReport = report;
            renderReport(report);
            long extIssues = report.findings().stream()
                    .filter(f -> f.ruleId().startsWith("EXTERNAL/")).count();
            if (extIssues > 0) {
                externalStatusLabel.setText("⚠ Online validation: " + extIssues
                        + " issue(s) — see findings tab");
            }
            progress.setVisible(false);
            validateButton.setDisable(false);
            exportButton.setDisable(false);
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
        new Thread(task, "tpt-validate").start();
        if (pStage != null) pStage.show();
    }

    @FXML
    private void onSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SettingsView.fxml"));
            Parent root = loader.load();
            SettingsController controller = loader.getController();

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Settings");
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            var result = dialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                AppSettings next = controller.collect();
                SettingsService.getInstance().update(next);
                ProxyConfig cfg = ProxyConfig.from(next.proxy(),
                        PasswordCipher.decrypt(next.proxy().manual().passwordEncrypted()));
                ProxyService.applyMode(cfg);
                externalEnabled.setSelected(next.external().enabled());
            }
        } catch (Exception e) {
            log.warn("Settings dialog failed: {}", e.getMessage());
        }
    }

    private void renderReport(QualityReport report) {
        scorePane.getChildren().clear();
        Map<ScoreCategory, Double> scores = report.scores();
        for (ScoreCategory cat : ScoreCategory.values()) {
            Double v = scores.get(cat);
            if (v == null) continue;
            scorePane.getChildren().add(buildScoreCard(cat.name(), v));
        }
        for (Map.Entry<Profile, Map<ScoreCategory, Double>> pe : report.perProfileScores().entrySet()) {
            Double v = pe.getValue().get(ScoreCategory.PROFILE_COMPLETENESS);
            if (v == null) continue;
            scorePane.getChildren().add(buildScoreCard(pe.getKey().displayName(), v));
        }

        allFindings.setAll(report.findings().stream().map(FindingRow::of).toList());
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

    @FXML
    private void onExport(ActionEvent e) {
        if (currentReport == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Excel report");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel workbook", "*.xlsx"));
        fc.setInitialFileName(currentReport.file().source().getFileName().toString().replaceFirst("\\.[^.]+$", "") + "_report.xlsx");
        File out = fc.showSaveDialog(stage);
        if (out == null) return;
        Path target = out.toPath();
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                new XlsxReportWriter(catalog).write(currentReport, target);
                return null;
            }
        };
        task.setOnSucceeded(ev -> statusLabel.setText("Report exported: " + target.getFileName()));
        task.setOnFailed(ev -> {
            Throwable th = task.getException();
            log.error("Export failed", th);
            statusLabel.setText("Export failed: " + (th == null ? "" : th.getMessage()));
            new Alert(Alert.AlertType.ERROR,
                    "Export failed:\n" + (th == null ? "(unknown)" : th.getMessage())).showAndWait();
        });
        new Thread(task, "tpt-export").start();
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
            com.tpt.validator.validation.FindingContext c =
                    f.context() == null ? com.tpt.validator.validation.FindingContext.EMPTY : f.context();
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

        /** Render the raw weight as a percentage with 2 decimals when parseable; otherwise pass through. */
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
