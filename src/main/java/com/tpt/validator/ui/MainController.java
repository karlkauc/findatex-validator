package com.tpt.validator.ui;

import com.tpt.validator.config.AppSettings;
import com.tpt.validator.config.PasswordCipher;
import com.tpt.validator.config.SettingsService;
import com.tpt.validator.external.proxy.ProxyConfig;
import com.tpt.validator.external.proxy.ProxyService;
import com.tpt.validator.template.api.TemplateDefinition;
import com.tpt.validator.template.api.TemplateRegistry;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level shell. Hosts the TabPane with one tab per registered template; per-template
 * behaviour lives in {@link TemplateTabController}. Keeps the global Settings dialog and
 * forwards the {@link Stage} reference to each tab.
 */
public final class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private final List<TemplateTabController> tabControllers = new ArrayList<>();
    private Stage stage;

    @FXML private TabPane templateTabs;

    public MainController() {
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        for (TemplateTabController ctrl : tabControllers) {
            ctrl.setStage(stage);
        }
    }

    @FXML
    public void initialize() {
        TemplateRegistry.init();
        for (TemplateDefinition def : TemplateRegistry.all()) {
            Tab tab = new Tab(def.displayName());
            tab.setClosable(false);
            // Probe spec availability: a missing or unreadable bundled spec means the template
            // is "installed" only structurally — show a placeholder instead of the full controls.
            try {
                def.specLoaderFor(def.latest()).load();
            } catch (Exception specEx) {
                log.info("Template {} {} spec not installed — showing placeholder tab ({})",
                        def.id(), def.latest().version(), specEx.getMessage());
                tab.setContent(buildMissingSpecPlaceholder());
                templateTabs.getTabs().add(tab);
                continue;
            }
            // Spec is loadable — wire up the full TemplateTab.fxml + controller.
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TemplateTab.fxml"));
                TemplateTabController ctrl = new TemplateTabController(def);
                loader.setController(ctrl);
                Parent content = loader.load();
                tab.setContent(content);
                templateTabs.getTabs().add(tab);
                tabControllers.add(ctrl);
            } catch (Exception e) {
                log.error("Failed to build tab for template {}", def.id(), e);
                tab.setContent(buildMissingSpecPlaceholder());
                templateTabs.getTabs().add(tab);
            }
        }
    }

    private static VBox buildMissingSpecPlaceholder() {
        Label notice = new Label("Spec nicht installiert — siehe docs/SPEC_DOWNLOADS.md");
        notice.getStyleClass().add("status-label");
        VBox box = new VBox(notice);
        box.setPadding(new Insets(40));
        return box;
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
            }
        } catch (Exception e) {
            log.warn("Settings dialog failed: {}", e.getMessage());
        }
    }
}
