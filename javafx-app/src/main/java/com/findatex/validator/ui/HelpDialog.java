package com.findatex.validator.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Modal Help window. Loads the canonical {@code help/HELP.md} bundled in the core JAR and renders
 * it via {@link MarkdownRenderer}. Links open in the user's default browser via {@link Desktop}
 * — no JavaFX WebView dependency, no embedded JxBrowser.
 */
public final class HelpDialog {

    private static final Logger log = LoggerFactory.getLogger(HelpDialog.class);
    private static final String RESOURCE_PATH = "/help/HELP.md";

    private HelpDialog() {
    }

    public static void show(Stage owner) {
        String markdown = loadMarkdown();
        VBox content = MarkdownRenderer.render(markdown, HelpDialog::openLink);
        content.setPadding(new Insets(20, 28, 20, 28));
        content.setMaxWidth(820);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Button closeButton = new Button("Close");
        closeButton.setDefaultButton(true);
        HBox actionBar = new HBox(closeButton);
        actionBar.setPadding(new Insets(10, 16, 12, 16));
        actionBar.setStyle("-fx-background-color: #f7f8fa; -fx-border-color: #e1e5ec; -fx-border-width: 1 0 0 0;");
        actionBar.setSpacing(8);
        actionBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        BorderPane root = new BorderPane();
        root.setCenter(scroll);
        root.setBottom(actionBar);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Scene scene = new Scene(root, 880, 720);
        Stage stage = new Stage();
        stage.setTitle("FinDatEx Validator — Help");
        stage.setScene(scene);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
            if (!owner.getIcons().isEmpty()) stage.getIcons().addAll(owner.getIcons());
        }
        closeButton.setOnAction(e -> stage.close());
        stage.show();
    }

    private static String loadMarkdown() {
        try (InputStream in = HelpDialog.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                log.warn("Missing classpath resource {}", RESOURCE_PATH);
                return "# Help unavailable\n\nThe bundled HELP.md resource was not found.";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to load {}: {}", RESOURCE_PATH, e.toString());
            return "# Help unavailable\n\nCould not load HELP.md: " + e.getMessage();
        }
    }

    private static void openLink(String url) {
        if (url == null || url.isBlank()) return;
        try {
            if (!Desktop.isDesktopSupported()) return;
            Desktop d = Desktop.getDesktop();
            if (d.isSupported(Desktop.Action.BROWSE)) d.browse(URI.create(url));
        } catch (Exception e) {
            log.debug("Could not open {}: {}", url, e.toString());
        }
    }

    /** Convenience for callers that don't have a Stage handy (e.g. unit-testing the dialog). */
    public static void show() {
        show(null);
    }

    /**
     * Factory for the placeholder shown when the Help bundle is missing — used by tests so they
     * can assert on the exact error copy without needing the resource on the classpath.
     */
    static Label missingHelpLabel() {
        return new Label("Help unavailable");
    }
}
