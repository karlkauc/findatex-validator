package com.tpt.validator.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

public final class LookupProgressController {

    @FXML private Label phaseLabel, gleifLabel, figiLabel, cacheLabel;
    @FXML private ProgressBar gleifBar, figiBar;
    @FXML private Button cancelButton;

    private volatile boolean cancelled;
    private Stage stage;

    public void setStage(Stage stage) { this.stage = stage; }

    public boolean isCancelled() { return cancelled; }

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
