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
 * Modal About window. Loads the bundled {@code about/ABOUT.md} and renders it
 * via {@link MarkdownRenderer}. External links open in the user's default
 * browser via {@link Desktop} — same approach as {@link HelpDialog}.
 */
public final class AboutDialog {

    private static final Logger log = LoggerFactory.getLogger(AboutDialog.class);
    private static final String RESOURCE_PATH = "/about/ABOUT.md";

    private AboutDialog() {
    }

    public static void show(Stage owner) {
        String markdown = loadMarkdown();
        VBox content = MarkdownRenderer.render(markdown, AboutDialog::openLink);
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
        stage.setTitle("FinDatEx Validator — About");
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
        try (InputStream in = AboutDialog.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                log.warn("Missing classpath resource {}", RESOURCE_PATH);
                return "# About unavailable\n\nThe bundled ABOUT.md resource was not found.";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to load {}: {}", RESOURCE_PATH, e.toString());
            return "# About unavailable\n\nCould not load ABOUT.md: " + e.getMessage();
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

    /** Convenience for callers that don't have a Stage handy (e.g. unit-testing). */
    public static void show() {
        show(null);
    }

    static Label missingAboutLabel() {
        return new Label("About unavailable");
    }
}
