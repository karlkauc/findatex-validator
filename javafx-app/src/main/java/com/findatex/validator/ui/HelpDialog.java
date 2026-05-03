package com.findatex.validator.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modal Help window. Top tab loads the canonical {@code help/HELP.md}; further tabs load each
 * per-template validation reference produced by {@code RuleDocGenerator} (one tab per
 * {@code (template, version)} pair, discovered via {@code help/rules/index.json} on the
 * classpath). Markdown is rendered via {@link MarkdownRenderer} and links open in the user's
 * default browser via {@link Desktop} — no JavaFX WebView dependency.
 */
public final class HelpDialog {

    private static final Logger log = LoggerFactory.getLogger(HelpDialog.class);
    private static final String HELP_RESOURCE = "/help/HELP.md";
    private static final String RULES_INDEX_RESOURCE = "/help/rules/index.json";
    private static final String RULES_DIR_RESOURCE = "/help/rules/";

    private HelpDialog() {
    }

    public static void show(Stage owner) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(buildTab("Operator guide", loadClasspath(HELP_RESOURCE,
                "# Help unavailable\n\nThe bundled HELP.md resource was not found.")));

        for (RulesEntry e : loadRulesIndex()) {
            String md = loadClasspath(RULES_DIR_RESOURCE + e.slug + ".md", null);
            if (md != null) {
                tabs.getTabs().add(buildTab(e.tabLabel(), md));
            }
        }

        Button closeButton = new Button("Close");
        closeButton.setDefaultButton(true);
        HBox actionBar = new HBox(closeButton);
        actionBar.setPadding(new Insets(10, 16, 12, 16));
        actionBar.setStyle("-fx-background-color: #f7f8fa; -fx-border-color: #e1e5ec; -fx-border-width: 1 0 0 0;");
        actionBar.setSpacing(8);
        actionBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        BorderPane root = new BorderPane();
        root.setCenter(tabs);
        root.setBottom(actionBar);

        Scene scene = new Scene(root, 980, 760);
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

    private static Tab buildTab(String label, String markdown) {
        VBox content = MarkdownRenderer.render(markdown, HelpDialog::openLink);
        content.setPadding(new Insets(20, 28, 20, 28));
        content.setMaxWidth(900);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Tab tab = new Tab(label);
        tab.setContent(scroll);
        return tab;
    }

    private static String loadClasspath(String resource, String fallback) {
        try (InputStream in = HelpDialog.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("Missing classpath resource {}", resource);
                return fallback;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to load {}: {}", resource, e.toString());
            return fallback;
        }
    }

    /** Lightweight description of one entry in the generated rules index. */
    static final class RulesEntry {
        final String slug;
        final String templateDisplayName;
        final String version;

        RulesEntry(String slug, String templateDisplayName, String version) {
            this.slug = slug;
            this.templateDisplayName = templateDisplayName;
            this.version = version;
        }

        String tabLabel() { return templateDisplayName + " " + version + " rules"; }
    }

    /**
     * Parses {@code help/rules/index.json} into entries without pulling in a JSON dependency.
     * Returns an empty list if the resource isn't on the classpath (fresh checkout, generator
     * never run).
     */
    static List<RulesEntry> loadRulesIndex() {
        String json = loadClasspath(RULES_INDEX_RESOURCE, null);
        List<RulesEntry> out = new ArrayList<>();
        if (json == null) return out;
        Pattern entry = Pattern.compile("\\{[^}]*\\}", Pattern.DOTALL);
        Pattern field = Pattern.compile("\"(slug|templateDisplayName|version)\"\\s*:\\s*\"([^\"]*)\"");
        Matcher entries = entry.matcher(json);
        while (entries.find()) {
            String fragment = entries.group();
            String slug = null, name = null, version = null;
            Matcher fm = field.matcher(fragment);
            while (fm.find()) {
                switch (fm.group(1)) {
                    case "slug" -> slug = fm.group(2);
                    case "templateDisplayName" -> name = fm.group(2);
                    case "version" -> version = fm.group(2);
                    default -> { /* ignore */ }
                }
            }
            if (slug != null && name != null && version != null) {
                out.add(new RulesEntry(slug, name, version));
            }
        }
        return out;
    }

    private static void openLink(String url) {
        SafeLinkOpener.open(url);
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
