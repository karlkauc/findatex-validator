package com.tpt.validator;

import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.spec.SpecLoader;
import com.tpt.validator.ui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Taskbar;
import java.awt.Toolkit;
import java.io.InputStream;
import java.util.Objects;

public final class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    /** Set the AWT app name early so the macOS menu bar / dock label reads "TPT Validator", not "App". */
    static {
        // -Xdock:name on macOS, plus the Apple-specific system properties.
        System.setProperty("apple.awt.application.name", "TPT Validator");
        System.setProperty("apple.awt.application.appearance", "system");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "TPT Validator");
    }

    public static void main(String[] args) {
        // Setting the dock/taskbar icon must happen before the toolkit shows the first window.
        setPlatformDockIcon();
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        SpecCatalog catalog = SpecLoader.loadBundled();

        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/MainView.fxml")));
        MainController controller = new MainController(catalog);
        loader.setController(controller);
        Parent root = loader.load();
        controller.setStage(stage);

        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        loadStageIcons(stage);

        stage.setTitle("TPT V7 Validator");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * JavaFX Stage icons cover Windows window/title-bar and (when the EXE has
     * its own icon embedded) the taskbar. We register every available size so
     * the OS picks the best match for each context.
     */
    private static void loadStageIcons(Stage stage) {
        for (int size : new int[]{16, 32, 48, 64, 128, 256, 512}) {
            try (InputStream in = App.class.getResourceAsStream("/icons/icon-" + size + ".png")) {
                if (in != null) stage.getIcons().add(new Image(in));
            } catch (Exception e) {
                log.debug("Could not load /icons/icon-{}.png: {}", size, e.toString());
            }
        }
    }

    /**
     * Sets the OS-level dock / taskbar icon via {@link Taskbar} (java.awt).
     * <p>On macOS this is the only way to control the dock icon when running
     * from a fat-jar (without an .app bundle). On Windows it improves the
     * taskbar grouping icon when launched from a non-bundled JAR. On Linux
     * GNOME/KDE this often has no effect — the desktop file installed by
     * jpackage is the right vehicle there.
     */
    private static void setPlatformDockIcon() {
        try {
            if (!Taskbar.isTaskbarSupported()) return;
            Taskbar tb = Taskbar.getTaskbar();
            if (!tb.isSupported(Taskbar.Feature.ICON_IMAGE)) return;
            // Pick the largest available icon — macOS scales it to the dock
            // size and uses the high-resolution version on retina displays.
            String resource = pickBestIconResource();
            if (resource == null) return;
            try (InputStream in = App.class.getResourceAsStream(resource)) {
                if (in == null) return;
                java.awt.Image awt = Toolkit.getDefaultToolkit().createImage(in.readAllBytes());
                tb.setIconImage(awt);
            }
        } catch (UnsupportedOperationException | SecurityException e) {
            // macOS sandbox or headless environment — ignore silently.
            log.debug("Taskbar icon not supported: {}", e.toString());
        } catch (Exception e) {
            log.debug("Failed to set platform dock icon: {}", e.toString());
        }
    }

    private static String pickBestIconResource() {
        // macOS prefers the highest-res raster and downsamples; Windows is
        // happy with the same. Try 512 first and fall back gracefully.
        for (int size : new int[]{512, 256, 128, 64}) {
            String r = "/icons/icon-" + size + ".png";
            if (App.class.getResource(r) != null) return r;
        }
        return null;
    }
}
