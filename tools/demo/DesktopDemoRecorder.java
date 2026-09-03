import com.findatex.validator.App;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives the JavaFX desktop app through a scripted walkthrough and dumps one
 * screen capture per animation frame plus a JSON manifest (caption, cursor
 * position, highlighted control, hold time) that {@code build_demo_gif.py}
 * turns into the README GIFs.
 *
 * <p>Run via {@code tools/demo/record_desktop_demo.sh} — it needs a display
 * (Xvfb is fine), the shaded desktop jar on the classpath and an isolated
 * {@code -Duser.home} so the demo neither reads the developer's settings nor
 * posts a usage event. Frames are written as PNG through {@code javax.imageio}
 * (the shaded jar has no javafx.swing, so the pixels are copied by hand).
 *
 * <p>The mouse pointer is not part of a screen capture, so its position is
 * recorded per frame and painted in afterwards; the same goes for the
 * caption bar and the highlight box.
 */
public final class DesktopDemoRecorder {

    private static final String TITLE = "FinDatEx Validator";
    private static final double STAGE_X = 40, STAGE_Y = 20;

    private static Path outDir;
    private static Stage stage;
    private static Robot robot;
    private static int frameNo;
    private static final List<String> manifest = new ArrayList<>();
    private static String part = "validate";
    private static String caption = "";
    private static Node highlight;
    private static double curX = 900, curY = 600;   // screen coords

    public static void main(String[] args) throws Exception {
        outDir = Path.of(args[0]);
        Path singleFile = Path.of(args[1]);
        Path batchFolder = Path.of(args[2]);
        Files.createDirectories(outDir);

        new Thread(() -> Application.launch(App.class), "demo-app").start();
        waitForStage();
        fx(() -> {
            robot = new Robot();
            stage.setX(STAGE_X);
            stage.setY(STAGE_Y);
            robot.mouseMove(curX, curY);
        });
        sleep(1500);

        try {
            recordValidate(singleFile);
            recordResults();
            recordBatch(batchFolder);
        } catch (Throwable t) {
            t.printStackTrace();
            Files.writeString(outDir.resolve("FAILED"), String.valueOf(t));
        }
        Files.writeString(outDir.resolve("manifest.json"),
                "[\n" + String.join(",\n", manifest) + "\n]\n", StandardCharsets.UTF_8);
        System.out.println("[demo] " + frameNo + " frames written to " + outDir);
        System.exit(0);
    }

    // ===== Scenes ==========================================================

    private static void recordValidate(Path file) throws Exception {
        part = "validate";
        caption("Step 1 — Pick the template tab (TPT, EET, EMT or EPT) and the spec version.", null);
        hold(1600);

        TabPane tabs = (TabPane) stage.getScene().lookup("#templateTabs");
        Node eetTab = tabHeader(tabs, 1);
        moveTo(eetTab, 10);
        click();
        hold(900);
        Node tptTab = tabHeader(tabs, 0);
        moveTo(tptTab, 8);
        click();
        hold(600);

        ComboBox<?> combo = (ComboBox<?>) n("versionCombo");
        highlight = combo;
        moveTo(combo, 10);
        click();
        hold(900);
        key(KeyCode.DOWN);
        hold(700);
        key(KeyCode.ENTER);
        hold(1400);
        highlight = null;

        caption("Step 2 — Choose a file, a whole folder (batch mode), or drag & drop it onto the window.", null);
        Node browse = n("browseButton");
        highlight = browse;
        moveTo(browse, 12);
        hold(1000);
        TextField field = (TextField) n("filePathField");
        highlight = field;
        typewriter(field, file.toString());
        hold(1200);
        highlight = null;

        caption("Step 3 — Keep only the regulatory profiles the delivery has to satisfy (all are ticked by default).", null);
        List<Node> boxes = new ArrayList<>(fx(() -> n("profilePane").lookupAll(".check-box")));
        highlight = n("profilePane");
        moveTo(boxes.get(boxes.size() - 1), 12);
        click();
        hold(1100);
        highlight = null;

        caption("Optional — cross-check LEIs and ISINs online against GLEIF and OpenFIGI (off by default).", null);
        highlight = n("externalRow");
        moveTo(n("externalEnabled"), 10);
        hold(1600);
        highlight = null;

        caption("Step 4 — Validate. Everything runs on your machine; nothing is uploaded.", null);
        Node validate = n("validateButton");
        highlight = validate;
        moveTo(validate, 12);
        hold(500);
        click();
        highlight = null;
        waitForValidation();
        hold(1800);
    }

    private static void recordResults() throws Exception {
        part = "results";
        ScrollPane sp = (ScrollPane) tabContent();
        caption("Result — one row per file with score and error counts, plus a quality score (0–100) overall and per profile.", null);
        scrollTo(sp, n("filesTable"), 7, 40);
        highlight = n("scorePane");
        hold(2600);
        highlight = null;

        caption("Findings — every problem with fund, row, field, rule and message. Filter by severity…", null);
        scrollTo(sp, n("filterErrors"), 7, 40);
        TableView<?> findings = (TableView<?>) n("findingsTable");
        highlight = findings;
        moveTo(n("filterWarnings"), 10);
        hold(600);
        click();
        hold(1400);
        highlight = null;

        caption("…or group by error to read one line per rule with its occurrence count.", null);
        Node group = n("groupByError");
        highlight = group;
        moveTo(group, 10);
        click();
        hold(500);
        highlight = findings;
        hold(2200);
        highlight = null;

        caption("Wrong finding? \"Report a false positive\" opens a pre-filled GitHub issue for you to review and submit.", null);
        Node row = topmostRow(findings);
        moveTo(row, 10);
        click();
        hold(500);
        Node fp = n("reportFpButton");
        highlight = fp;
        moveTo(fp, 10);
        hold(2200);
        highlight = null;

        recordAnnotatedSource(sp, findings, group);

        caption("Export the Excel report — one row per finding, six sheets, ready for your source-system team.", null);
        scrollTo(sp, n("validateButton"), 7, 48);
        Node export = n("exportMenu");
        highlight = export;
        moveTo(export, 10);
        click();
        hold(2400);
        key(KeyCode.ESCAPE);
        hold(600);
        highlight = null;
    }

    /** The in-app Annotated Source tab: tinted grid, filters, tooltip, jump from a finding. */
    private static void recordAnnotatedSource(ScrollPane sp, TableView<?> findings, Node group) throws Exception {
        caption("Annotated Source — the original file cell by cell, every cell with a finding tinted by severity.", null);
        TabPane resultTabs = (TabPane) n("resultTabs");
        scrollTo(sp, resultTabs, 7, 40);
        Node sourceTabHeader = tabHeader(resultTabs, 1);
        moveTo(sourceTabHeader, 10);
        click();
        Node host = n("annotatedSourceHost");
        TableView<?> grid = waitForGrid(host);
        highlight = grid;
        hold(2200);
        highlight = null;

        caption("Keep only the rows and columns that carry a finding — the overview of a large delivery in one screen.", null);
        List<Node> boxes = new ArrayList<>(fx(() -> host.lookupAll(".check-box")));
        highlight = boxes.get(0);
        moveTo(boxes.get(0), 10);
        click();
        hold(700);
        highlight = boxes.get(1);
        moveTo(boxes.get(1), 8);
        click();
        hold(1400);
        highlight = null;

        caption("Double-click a finding to jump straight to the offending cell.", null);
        Node findingsTabHeader = tabHeader(resultTabs, 0);
        moveTo(findingsTabHeader, 8);
        click();
        hold(500);
        moveTo(group, 8);      // leave "group by error" so rows carry their position again
        click();
        hold(500);
        Node row = topmostRow(findings);
        moveTo(row, 10);
        doubleClick();
        hold(600);
        highlight = grid;
        hold(2400);
        highlight = null;

        caption("Hover a tinted cell to read its findings — the same text as the comment in the Excel sheet.", null);
        Node tinted = tintedDataCell(grid);
        if (tinted != null) {
            moveTo(tinted, 12);
            sleep(900);           // tooltip show delay
            hold(2600);
            fx(() -> robot.mouseMove(curX, curY + 80));   // leave the cell so the tooltip hides
            curY += 80;
            sleep(300);
        }

        // Leave the tab as we found it: filters off, Findings in front (the batch scene expects it).
        fx(() -> { ((javafx.scene.control.CheckBox) boxes.get(0)).setSelected(false);
                   ((javafx.scene.control.CheckBox) boxes.get(1)).setSelected(false); });
        moveTo(findingsTabHeader, 6);
        click();
        hold(300);
    }

    private static TableView<?> waitForGrid(Node host) throws Exception {
        for (int i = 0; i < 200; i++) {
            TableView<?> t = fx(() -> (TableView<?>) host.lookup(".source-grid"));
            if (t != null && fx(() -> t.getColumns().size() > 0)) return t;
            sleep(100);
        }
        throw new IllegalStateException("annotated source grid never loaded");
    }

    /**
     * Leftmost tinted data cell (not the Row helper column) inside the grid's viewport, errors
     * first — leftmost so the tooltip that opens to the right of the cursor stays on screen.
     */
    private static Node tintedDataCell(TableView<?> grid) throws Exception {
        return fx(() -> {
            Bounds view = grid.localToScene(grid.getBoundsInLocal());
            for (String cls : List.of(".source-cell-error", ".source-cell-warn", ".source-cell-info")) {
                Node best = null;
                double bestX = Double.MAX_VALUE;
                for (Node c : grid.lookupAll(cls)) {
                    if (c.getStyleClass().contains("source-row-col")) continue;
                    Bounds b = c.localToScene(c.getBoundsInLocal());
                    boolean inView = b.getMinX() >= view.getMinX() && b.getMaxX() <= view.getMaxX()
                            && b.getMinY() >= view.getMinY() && b.getMaxY() <= view.getMaxY() - 20;
                    if (inView && b.getMinX() < bestX) { best = c; bestX = b.getMinX(); }
                }
                if (best != null) return best;
            }
            return null;
        });
    }

    private static void recordBatch(Path folder) throws Exception {
        part = "batch";
        ScrollPane sp = (ScrollPane) tabContent();
        scrollTo(sp, null, 7, 0);
        caption("Batch mode — choose a folder instead of a file and every delivery in it is validated in one run.", null);
        Node browseFolder = n("browseFolderButton");
        highlight = browseFolder;
        moveTo(browseFolder, 10);
        hold(700);   // a real click opens the native DirectoryChooser, which needs a desktop
        enterBatchMode(folder);
        // Clearing the (focused) Files table makes the ScrollPane jump to it — go back to the form.
        scrollTo(sp, null, 5, 0);
        highlight = n("batchModeLabel");
        hold(1600);
        highlight = null;

        caption("Validate — the Files table fills up as each file completes; click a row to see its findings.", null);
        Node validate = n("validateButton");
        highlight = validate;
        moveTo(validate, 10);
        click();
        highlight = null;
        waitForValidation();
        hold(600);
        scrollTo(sp, n("filesTable"), 7, 40);
        hold(800);
        TableView<?> files = (TableView<?>) n("filesTable");
        highlight = files;
        moveTo(rowAt(files, 2), 10);
        click();
        hold(1600);
        highlight = null;

        caption("Export — per file, one combined report, or combined with the annotated source file.", null);
        scrollTo(sp, n("validateButton"), 7, 48);
        Node export = n("exportMenu");
        highlight = export;
        moveTo(export, 10);
        click();
        hold(2400);
        key(KeyCode.ESCAPE);
        hold(500);
        highlight = null;
    }

    // ===== Driving helpers =================================================

    private static void caption(String text, Node hl) {
        caption = text;
        highlight = hl;
    }

    private static Node tabHeader(TabPane tabs, int index) throws Exception {
        List<Node> headers = new ArrayList<>(fx(() -> tabs.lookupAll(".tab")));
        return headers.get(index);
    }

    /** Visible rows of a TableView, sorted top to bottom (lookupAll order is not layout order). */
    private static List<Node> rowsTopDown(TableView<?> table) throws Exception {
        return fx(() -> table.lookupAll(".table-row-cell").stream()
                .filter(r -> r.isVisible() && r.getBoundsInParent().getHeight() > 0)
                .sorted(java.util.Comparator.comparingDouble(r -> r.localToScene(r.getBoundsInLocal()).getMinY()))
                .toList());
    }

    private static Node topmostRow(TableView<?> table) throws Exception {
        return rowsTopDown(table).get(0);
    }

    private static Node rowAt(TableView<?> table, int index) throws Exception {
        List<Node> rows = rowsTopDown(table);
        return rows.get(Math.min(index, rows.size() - 1));
    }

    private static Node tabContent() throws Exception {
        return fx(() -> ((TabPane) stage.getScene().lookup("#templateTabs"))
                .getSelectionModel().getSelectedItem().getContent());
    }

    private static Node n(String fxId) throws Exception {
        Node content = tabContent();
        return Objects.requireNonNull(fx(() -> content.lookup("#" + fxId)), "no node #" + fxId);
    }

    private static Point2D center(Node node) throws Exception {
        return fx(() -> {
            Bounds b = node.localToScreen(node.getBoundsInLocal());
            return new Point2D(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2);
        });
    }

    private static void moveTo(Node node, int frames) throws Exception {
        Point2D target = center(node);
        double sx = curX, sy = curY;
        for (int i = 1; i <= frames; i++) {
            double t = i / (double) frames;
            double e = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;   // ease in-out
            curX = sx + (target.getX() - sx) * e;
            curY = sy + (target.getY() - sy) * e;
            fx(() -> robot.mouseMove(curX, curY));
            frame(50);
        }
    }

    private static void click() throws Exception {
        fx(() -> robot.mouseClick(MouseButton.PRIMARY));
        sleep(150);
        frame(250);
    }

    private static void doubleClick() throws Exception {
        fx(() -> { robot.mouseClick(MouseButton.PRIMARY); robot.mouseClick(MouseButton.PRIMARY); });
        sleep(150);
        frame(250);
    }

    private static void key(KeyCode code) throws Exception {
        fx(() -> robot.keyType(code));
        sleep(150);
        frame(250);
    }

    private static void typewriter(TextField field, String text) throws Exception {
        fx(() -> field.requestFocus());
        for (int i = 1; i <= text.length(); i++) {
            String prefix = text.substring(0, i);
            fx(() -> { field.setText(prefix); field.positionCaret(prefix.length()); });
            frame(i == text.length() ? 400 : 40);
        }
    }

    /** Scrolls the tab so that {@code node} sits {@code margin} px below the viewport top (null = top). */
    private static void scrollTo(ScrollPane sp, Node node, int frames, double margin) throws Exception {
        double from = fx(sp::getVvalue);
        double to = node == null ? 0.0 : fx(() -> {
            Node content = sp.getContent();
            Bounds inContent = content.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
            double vmax = content.getBoundsInLocal().getHeight() - sp.getViewportBounds().getHeight();
            if (vmax <= 0) return 0.0;
            return Math.max(0, Math.min(1, (inContent.getMinY() - margin) / vmax));
        });
        for (int i = 1; i <= frames; i++) {
            double t = i / (double) frames;
            double e = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            double v = from + (to - from) * e;
            fx(() -> sp.setVvalue(v));
            frame(50);
        }
    }

    private static void waitForValidation() throws Exception {
        Node validate = n("validateButton");
        Node progress = n("progress");
        for (int i = 0; i < 400; i++) {
            boolean running = fx(() -> validate.isDisabled() || progress.isVisible());
            frame(120);
            if (!running && i > 2) return;
            sleep(60);
        }
    }

    private static void hold(long ms) throws Exception {
        sleep(120);
        frame(ms);
    }

    /**
     * Programmatic stand-in for the DirectoryChooser / folder drop, which need a real desktop.
     * The controller is not reachable from the scene graph, but the FXML-generated onAction
     * handler holds it; requires {@code --add-opens javafx.fxml/javafx.fxml=ALL-UNNAMED}.
     */
    private static void enterBatchMode(Path folder) throws Exception {
        Node browseFolder = n("browseFolderButton");
        Object handler = fx(() -> ((javafx.scene.control.Button) browseFolder).getOnAction());
        Object controller = Objects.requireNonNull(findController(handler, 0), "controller not found in handler");
        Method m = controller.getClass().getDeclaredMethod("enterBatchMode", Path.class);
        m.setAccessible(true);
        fx(() -> { m.invoke(controller, folder); return null; });
    }

    private static Object findController(Object o, int depth) throws Exception {
        if (o == null || depth > 3) return null;
        if (o.getClass().getSimpleName().equals("TemplateTabController")) return o;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().isPrimitive() || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object found = findController(f.get(o), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ===== Capture =========================================================

    private static void frame(long holdMs) throws Exception {
        String file = String.format("frame-%04d.png", frameNo++);
        double[] hl = highlight == null ? null : fx(() -> {
            Bounds b = highlight.localToScreen(highlight.getBoundsInLocal());
            return new double[]{b.getMinX() - stage.getX(), b.getMinY() - stage.getY(), b.getWidth(), b.getHeight()};
        });
        WritableImage img = fx(() -> robot.getScreenCapture(null,
                new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight())));
        writePng(img, outDir.resolve(file));
        manifest.add(String.format(java.util.Locale.ROOT,
                "{\"file\":\"%s\",\"part\":\"%s\",\"ms\":%d,\"caption\":%s,\"cursor\":[%.1f,%.1f],\"highlight\":%s}",
                file, part, holdMs, json(caption), curX - stage.getX(), curY - stage.getY(),
                hl == null ? "null" : String.format(java.util.Locale.ROOT,
                        "[%.1f,%.1f,%.1f,%.1f]", hl[0], hl[1], hl[2], hl[3])));
    }

    private static void writePng(WritableImage img, Path file) throws IOException {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        int[] argb = new int[w * h];
        img.getPixelReader().getPixels(0, 0, w, h, WritablePixelFormat.getIntArgbInstance(), argb, 0, w);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, argb, 0, w);
        ImageIO.write(out, "png", file.toFile());
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ===== FX plumbing =====================================================

    private static void waitForStage() throws Exception {
        for (int i = 0; i < 300; i++) {
            sleep(200);
            try {
                Stage s = fx(() -> Window.getWindows().stream()
                        .filter(w -> w instanceof Stage st && st.isShowing() && TITLE.equals(st.getTitle()))
                        .map(w -> (Stage) w).findFirst().orElse(null));
                if (s != null) { stage = s; return; }
            } catch (IllegalStateException toolkitNotReady) {
                // FX not initialised yet
            }
        }
        throw new IllegalStateException("main stage did not appear");
    }

    private static <T> T fx(Callable<T> c) throws Exception {
        if (Platform.isFxApplicationThread()) return c.call();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try { result.set(c.call()); }
            catch (Exception e) { error.set(e); }
            finally { latch.countDown(); }
        });
        latch.await();
        if (error.get() != null) throw error.get();
        return result.get();
    }

    private static void fx(Runnable r) throws Exception {
        fx(() -> { r.run(); return null; });
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
