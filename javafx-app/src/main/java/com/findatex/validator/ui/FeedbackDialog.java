package com.findatex.validator.ui;

import com.findatex.validator.feedback.GitHubIssueLink;
import com.findatex.validator.feedback.GitHubIssueLink.FalsePositiveReport;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Confirmation dialog for "Report a false positive". Shows the <em>exact</em>
 * GitHub issue body that will be pre-filled (incl. the reported value and fund
 * context) plus an editable comment, so nothing leaves the machine without the
 * user seeing it and explicitly pressing the confirm button. On confirm the
 * pre-filled GitHub "New Issue" URL is opened in the default browser; the user
 * still has to press <em>Submit</em> on GitHub themselves.
 */
public final class FeedbackDialog {

    private static final Logger log = LoggerFactory.getLogger(FeedbackDialog.class);

    private FeedbackDialog() {}

    public static void show(Stage owner, String repoSlug, FalsePositiveReport report) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Report a false positive");
        dialog.setHeaderText("This opens a pre-filled GitHub issue in your browser.\n"
                + "Review exactly what will be submitted, then press \"Open GitHub issue…\".");
        if (owner != null) {
            dialog.initOwner(owner);
            if (!owner.getIcons().isEmpty()) {
                ((Stage) dialog.getDialogPane().getScene().getWindow()).getIcons()
                        .addAll(owner.getIcons());
            }
        }

        ButtonType openType = new ButtonType("Open GitHub issue…", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(openType, ButtonType.CANCEL);

        Label commentLabel = new Label("Why is this a false positive? (optional, recommended)");
        TextArea comment = new TextArea();
        comment.setPromptText("e.g. field 12 is not mandatory for our profile because …");
        comment.setWrapText(true);
        comment.setPrefRowCount(3);

        Label previewLabel = new Label("Exactly this will be placed in the GitHub issue:");
        TextArea preview = new TextArea(GitHubIssueLink.issueBody(report));
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(16);
        preview.getStyleClass().add("monospace");
        VBox.setVgrow(preview, Priority.ALWAYS);

        // Live-refresh the preview so the body shown is always what gets sent.
        comment.textProperty().addListener((o, was, is) ->
                preview.setText(GitHubIssueLink.issueBody(
                        withComment(report, is))));

        VBox box = new VBox(8, commentLabel, comment, previewLabel, preview);
        box.setPadding(new Insets(12));
        box.setPrefWidth(720);
        box.setPrefHeight(560);
        dialog.getDialogPane().setContent(box);
        dialog.setResizable(true);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == openType) {
                String url = GitHubIssueLink.issueUrl(repoSlug,
                        withComment(report, comment.getText()));
                log.info("Opening GitHub false-positive issue ({} chars) for {}",
                        url.length(), repoSlug);
                SafeLinkOpener.open(url);
            }
        });
    }

    private static FalsePositiveReport withComment(FalsePositiveReport r, String comment) {
        return new FalsePositiveReport(
                r.templateId(), r.templateVersion(), r.severity(), r.ruleId(), r.profile(),
                r.fieldNum(), r.fieldName(), r.value(), r.message(), r.portfolioId(),
                r.portfolioName(), r.valuationDate(), r.instrumentCode(), r.instrumentName(),
                r.valuationWeight(), r.appVersion(), comment);
    }
}
