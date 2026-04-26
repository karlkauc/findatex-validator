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

import java.io.InputStream;
import java.util.Objects;

public final class App extends Application {

    public static void main(String[] args) {
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

        loadAppIcons(stage);

        stage.setTitle("TPT V7 Validator");
        stage.setScene(scene);
        stage.show();
    }

    private static void loadAppIcons(Stage stage) {
        for (int size : new int[]{16, 32, 48, 64, 128, 256, 512}) {
            try (InputStream in = App.class.getResourceAsStream("/icons/icon-" + size + ".png")) {
                if (in != null) stage.getIcons().add(new Image(in));
            } catch (Exception ignored) { /* best effort — missing icon isn't fatal */ }
        }
    }
}
