package com.findatex.validator.ui;

import com.findatex.validator.batch.BatchProgress;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class LookupProgressController {

    @FXML private Label phaseLabel, subtitleLabel,
                        batchCounter, batchLabel,
                        gleifLabel, figiLabel, cacheLabel;
    @FXML private ProgressBar batchBar, gleifBar, figiBar;
    @FXML private VBox batchCard, gleifCard, figiCard;
    @FXML private Button cancelButton;

    private volatile boolean cancelled;
    private Stage stage;

    public void setStage(Stage stage) { this.stage = stage; }

    public boolean isCancelled() { return cancelled; }

    /** Show or hide the batch-level "Files: x/N" controls. Single-file callers leave this off. */
    public void setBatchMode(boolean enabled) {
        Platform.runLater(() -> {
            batchCard.setManaged(enabled);
            batchCard.setVisible(enabled);
            if (enabled) {
                phaseLabel.setText("Folder validation");
                subtitleLabel.setText("Validating files in selected folder");
            }
        });
    }

    /** Show or hide the GLEIF/OpenFIGI/cache rows. Off when external validation is disabled. */
    public void setExternalMode(boolean enabled) {
        Platform.runLater(() -> {
            gleifCard.setManaged(enabled);
            gleifCard.setVisible(enabled);
            figiCard.setManaged(enabled);
            figiCard.setVisible(enabled);
            cacheLabel.setManaged(enabled);
            cacheLabel.setVisible(enabled);
        });
    }

    /** Update the batch progress bar + label. Safe to call from any thread. */
    public void updateBatch(BatchProgress p) {
        if (p == null) return;
        Platform.runLater(() -> {
            int done = p.filesDone();
            int total = p.filesTotal();
            String fileName = p.currentFileName() == null ? "" : p.currentFileName();
            String phase = phaseLabel(p.phase());
            batchCounter.setText(total == 0 ? "—" : done + "/" + total);
            String detail = fileName.isEmpty()
                    ? phase
                    : (phase.isEmpty() ? fileName : fileName + " — " + phase);
            batchLabel.setText(detail);
            batchBar.setProgress(total == 0 ? 1 : (double) done / total);
            subtitleLabel.setText(total == 0
                    ? "Preparing…"
                    : "Processed " + done + " of " + total + " file(s)"
                      + (phase.isEmpty() ? "" : " — " + phase));
        });
    }

    private static String phaseLabel(BatchProgress.Phase phase) {
        if (phase == null) return "";
        return switch (phase) {
            case LOADING -> "loading";
            case VALIDATING -> "validating";
            case EXTERNAL -> "external lookup";
            case SCORING -> "scoring";
            case DONE -> "done";
        };
    }

    public void update(int gleifDone, int gleifTotal,
                       int figiDone,  int figiTotal,
                       int cacheHits, int cacheTotal) {
        Platform.runLater(() -> {
            gleifLabel.setText(gleifDone + "/" + gleifTotal);
            gleifBar.setProgress(gleifTotal == 0 ? 1 : (double) gleifDone / gleifTotal);
            figiLabel.setText(figiDone + "/" + figiTotal);
            figiBar.setProgress(figiTotal == 0 ? 1 : (double) figiDone / figiTotal);
            int pct = cacheTotal == 0 ? 0 : 100 * cacheHits / cacheTotal;
            cacheLabel.setText("Cache hits: " + cacheHits + " / " + cacheTotal + " (" + pct + "%)");
        });
    }

    public void close() { Platform.runLater(() -> { if (stage != null) stage.close(); }); }

    @FXML
    private void onCancel() {
        cancelled = true;
        cancelButton.setDisable(true);
        cancelButton.setText("Cancelling…");
        subtitleLabel.setText("Cancelling — finishing current file…");
    }
}
