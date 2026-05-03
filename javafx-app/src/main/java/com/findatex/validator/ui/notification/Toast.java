package com.findatex.validator.ui.notification;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.ref.WeakReference;

public final class Toast {

    private static final double WIDTH_PX = 360;
    private static final double MARGIN_PX = 16;
    private static final Duration FADE_DURATION = Duration.millis(250);
    private static final Duration AUTO_DISMISS = Duration.seconds(8);

    private static WeakReference<Popup> currentRef = new WeakReference<>(null);

    private Toast() {}

    public static void show(Stage owner, ToastInfo info) {
        if (owner == null || info == null) return;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(owner, info));
            return;
        }
        dismissCurrent();

        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(false);
        popup.setHideOnEscape(false);

        VBox root = buildRoot(info);
        root.setOpacity(0);
        popup.getContent().add(root);
        root.lookupAll(".toast-close").forEach(node ->
                ((Button) node).setOnAction(ev -> fadeOutAndHide(popup, root)));

        currentRef = new WeakReference<>(popup);
        positionAtBottomRight(popup, owner, root);
        popup.show(owner);
        positionAtBottomRight(popup, owner, root);

        FadeTransition fadeIn = new FadeTransition(FADE_DURATION, root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        PauseTransition autoDismiss = new PauseTransition(AUTO_DISMISS);
        autoDismiss.setOnFinished(ev -> fadeOutAndHide(popup, root));
        autoDismiss.play();

        root.setOnMouseEntered(ev -> autoDismiss.stop());
        root.setOnMouseExited(ev -> autoDismiss.playFromStart());
    }

    private static VBox buildRoot(ToastInfo info) {
        Label title = new Label(info.title());
        title.getStyleClass().add("toast-title");
        title.setWrapText(true);
        title.setMaxWidth(WIDTH_PX - 50);

        String meta = Formatters.humanBytes(info.bytes())
                + "  ·  " + Formatters.humanDuration(info.elapsed());
        Label metaLabel = new Label(meta);
        metaLabel.getStyleClass().add("toast-meta");

        Button closeBtn = new Button("×");
        closeBtn.getStyleClass().add("toast-close");
        closeBtn.setFocusTraversable(false);

        HBox titleRow = new HBox(8, title);
        HBox.setHgrow(title, javafx.scene.layout.Priority.ALWAYS);
        titleRow.getChildren().add(closeBtn);
        titleRow.setAlignment(Pos.TOP_LEFT);

        Button openBtn = new Button(info.isFolder() ? "Open folder" : "Open");
        openBtn.getStyleClass().add("toast-action");
        openBtn.setFocusTraversable(false);
        openBtn.setOnAction(ev -> openInDesktop(info.target().toFile()));

        HBox actions = new HBox(4, openBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (!info.isFolder() && info.target().getParent() != null) {
            Button revealBtn = new Button("Show in folder");
            revealBtn.getStyleClass().add("toast-action");
            revealBtn.setFocusTraversable(false);
            revealBtn.setOnAction(ev -> openInDesktop(info.target().getParent().toFile()));
            actions.getChildren().add(0, revealBtn);
        }

        VBox root = new VBox(6, titleRow, metaLabel, actions);
        root.getStyleClass().add("toast");
        root.setPrefWidth(WIDTH_PX);
        root.setMaxWidth(WIDTH_PX);
        return root;
    }

    private static void positionAtBottomRight(Popup popup, Window owner, Region root) {
        root.applyCss();
        root.layout();
        double w = root.getWidth() > 0 ? root.getWidth() : WIDTH_PX;
        double h = root.getHeight() > 0 ? root.getHeight() : root.prefHeight(WIDTH_PX);
        double x = owner.getX() + owner.getWidth() - w - MARGIN_PX;
        double y = owner.getY() + owner.getHeight() - h - MARGIN_PX;
        popup.setX(x);
        popup.setY(y);
    }

    private static void fadeOutAndHide(Popup popup, Region root) {
        if (root == null) {
            popup.hide();
            return;
        }
        FadeTransition fade = new FadeTransition(FADE_DURATION, root);
        fade.setFromValue(root.getOpacity());
        fade.setToValue(0);
        fade.setOnFinished(ev -> popup.hide());
        fade.play();
    }

    private static void dismissCurrent() {
        Popup prev = currentRef.get();
        if (prev != null && prev.isShowing()) {
            prev.hide();
        }
        currentRef = new WeakReference<>(null);
    }

    private static void openInDesktop(java.io.File file) {
        if (file == null) return;
        new Thread(() -> {
            try {
                if (!Desktop.isDesktopSupported()) {
                    Platform.runLater(() -> showError("Desktop action not available on this platform."));
                    return;
                }
                Desktop.getDesktop().open(file);
            } catch (IOException | UnsupportedOperationException ex) {
                Platform.runLater(() -> showError("Could not open: " + ex.getMessage()));
            }
        }, "toast-open").start();
    }

    private static void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }
}
