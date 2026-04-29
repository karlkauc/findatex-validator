package com.findatex.validator.ui;

import com.findatex.validator.batch.BatchProgress;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

public final class LookupProgressController {

    @FXML private Label phaseLabel, batchLabel, gleifLabel, figiLabel, cacheLabel;
    @FXML private ProgressBar batchBar, gleifBar, figiBar;
    @FXML private Button cancelButton;

    private volatile boolean cancelled;
    private Stage stage;

    public void setStage(Stage stage) { this.stage = stage; }

    public boolean isCancelled() { return cancelled; }

    /** Show or hide the batch-level "Files: x/N" controls. Single-file callers leave this off. */
    public void setBatchMode(boolean enabled) {
        Platform.runLater(() -> {
            batchLabel.setManaged(enabled);
            batchLabel.setVisible(enabled);
            batchBar.setManaged(enabled);
            batchBar.setVisible(enabled);
            if (enabled) {
                phaseLabel.setText("Folder validation");
            }
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
            String prefix = total == 0 ? "" : "Files: " + done + "/" + total;
            String label = fileName.isEmpty()
                    ? prefix + (phase.isEmpty() ? "" : " — " + phase)
                    : prefix + " — " + fileName + (phase.isEmpty() ? "" : " (" + phase + ")");
            batchLabel.setText(label);
            batchBar.setProgress(total == 0 ? 1 : (double) done / total);
        });
    }

    private static String phaseLabel(BatchProgress.Phase phase) {
        if (phase == null) return "";
        return switch (phase) {
            case LOADING -> "loading";
            case VALIDATING -> "validating";
            case EXTERNAL -> "external";
            case SCORING -> "scoring";
            case DONE -> "done";
        };
    }

    public void update(int gleifDone, int gleifTotal,
                       int figiDone,  int figiTotal,
                       int cacheHits, int cacheTotal) {
        Platform.runLater(() -> {
            gleifLabel.setText("GLEIF lookup: " + gleifDone + "/" + gleifTotal);
            gleifBar.setProgress(gleifTotal == 0 ? 1 : (double) gleifDone / gleifTotal);
            figiLabel.setText("OpenFIGI lookup: " + figiDone + "/" + figiTotal);
            figiBar.setProgress(figiTotal == 0 ? 1 : (double) figiDone / figiTotal);
            int pct = cacheTotal == 0 ? 0 : 100 * cacheHits / cacheTotal;
            cacheLabel.setText("Cache hits: " + cacheHits + " / " + cacheTotal + " (" + pct + "%)");
        });
    }

    public void close() { Platform.runLater(() -> { if (stage != null) stage.close(); }); }

    @FXML
    private void onCancel() { cancelled = true; cancelButton.setDisable(true); }
}
