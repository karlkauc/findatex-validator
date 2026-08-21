package com.findatex.validator.ui;

import com.findatex.validator.config.SettingsService;
import com.findatex.validator.quickfeedback.QuickFeedbackClient;
import com.findatex.validator.quickfeedback.QuickFeedbackEntry;
import com.findatex.validator.quickfeedback.QuickFeedbackStatus;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Low-barrier "rate this app" dialog: five stars, an optional comment, one
 * Send button. Posts through the shared {@link QuickFeedbackClient} to the
 * endpoint configured in Settings → Feedback (non-blank by default, so the
 * feature works out of the box; blank means the user disabled it). The network
 * call runs on a daemon thread — same pattern as the newsletter sign-up in
 * {@link SettingsController}.
 */
public final class QuickFeedbackDialog {

    private QuickFeedbackDialog() {
    }

    public static void show(Stage owner, String templateId) {
        int[] rating = {0};

        Label prompt = new Label("How useful is this validator for you?");
        prompt.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        ToggleButton[] stars = new ToggleButton[QuickFeedbackEntry.MAX_RATING];
        HBox starRow = new HBox(4);
        starRow.setAlignment(Pos.CENTER_LEFT);
        Button send = new Button("Send");
        for (int i = 0; i < stars.length; i++) {
            final int value = i + 1;
            ToggleButton star = new ToggleButton("☆");
            star.setStyle("-fx-font-size: 22px; -fx-background-color: transparent; -fx-padding: 0 4 0 4;");
            star.setFocusTraversable(false);
            star.setOnAction(e -> {
                rating[0] = value;
                for (int j = 0; j < stars.length; j++) {
                    stars[j].setText(j < value ? "★" : "☆");
                    stars[j].setSelected(j < value);
                }
                send.setDisable(false);
            });
            stars[i] = star;
            starRow.getChildren().add(star);
        }

        TextArea comment = new TextArea();
        comment.setPromptText("Optional: what works well, what is missing?");
        comment.setPrefRowCount(4);
        comment.setWrapText(true);
        comment.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= QuickFeedbackEntry.MAX_COMMENT_LENGTH ? change : null));

        Label result = new Label();
        result.setWrapText(true);

        Label privacy = new Label("Only your rating, optional comment, app version and "
                + "template type are sent — nothing else.");
        privacy.setWrapText(true);
        privacy.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11px;");

        send.setDisable(true);
        send.setDefaultButton(true);
        Button close = new Button("Close");
        HBox actionBar = new HBox(8, send, close);
        actionBar.setAlignment(Pos.CENTER_RIGHT);

        send.setOnAction(e -> {
            String endpoint = SettingsService.getInstance().getCurrent().quickFeedback().endpointUrl();
            if (endpoint == null || endpoint.isBlank()) {
                result.setText("Quick feedback is disabled (no endpoint configured in Settings → Feedback).");
                return;
            }
            result.setText("");
            send.setDisable(true);
            int chosen = rating[0];
            String text = comment.getText();

            Task<QuickFeedbackStatus> task = new Task<>() {
                @Override
                protected QuickFeedbackStatus call() {
                    return new QuickFeedbackClient().submit(endpoint, chosen, text, templateId);
                }
            };
            task.setOnSucceeded(ev -> {
                send.setDisable(false);
                result.setText(message(task.getValue()));
            });
            task.setOnFailed(ev -> {
                // QuickFeedbackClient never throws, but be defensive about the Task itself.
                send.setDisable(false);
                result.setText(message(QuickFeedbackStatus.UNAVAILABLE));
            });
            Thread t = new Thread(task, "quick-feedback-submit");
            t.setDaemon(true);
            t.start();
        });

        VBox root = new VBox(10, prompt, starRow, comment, result, privacy, actionBar);
        root.setPadding(new Insets(16, 20, 14, 20));

        Scene scene = new Scene(root, 440, 320);
        Stage stage = new Stage();
        stage.setTitle("Rate this app");
        stage.setScene(scene);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
            if (!owner.getIcons().isEmpty()) stage.getIcons().addAll(owner.getIcons());
        }
        close.setOnAction(e -> stage.close());
        stage.show();
    }

    private static String message(QuickFeedbackStatus status) {
        return switch (status) {
            case OK -> "Thank you for your feedback!";
            case INVALID -> "Please pick a star rating (comment max "
                    + QuickFeedbackEntry.MAX_COMMENT_LENGTH + " characters).";
            case RATE_LIMITED -> "Too many submissions — please try again later.";
            case UNAVAILABLE -> "Feedback is not possible right now. Please try again later.";
        };
    }
}
