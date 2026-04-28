package com.findatex.validator.ui;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import java.util.List;
import java.util.function.Consumer;

/**
 * Renders a CommonMark + GFM-tables Markdown source into a JavaFX node tree, suitable for placing
 * inside a ScrollPane. The output uses live JavaFX controls (Hyperlink, Label, GridPane) — no
 * embedded WebView, so the desktop-fat-jar size doesn't grow.
 *
 * <p>Block elements supported: headings (H1-H4), paragraphs, bullet/ordered lists, fenced code
 * blocks, block quotes, thematic breaks, GFM tables. Inline elements: bold, italic, inline code,
 * links, soft/hard line breaks. Anything else is rendered as plain text via the underlying
 * Visitor's default traversal.
 */
public final class MarkdownRenderer {

    private static final double TEXT_SIZE   = 13;
    private static final double H1_SIZE     = 22;
    private static final double H2_SIZE     = 18;
    private static final double H3_SIZE     = 15;
    private static final double H4_SIZE     = 13;
    private static final double CODE_SIZE   = 12;

    private static final String CODE_FONT_FAMILY = "monospace";
    private static final Insets BLOCK_GAP = new Insets(4, 0, 4, 0);

    private MarkdownRenderer() {
    }

    /** Parses {@code markdown} and returns a vertical container with one child per block element. */
    public static VBox render(String markdown, Consumer<String> linkOpener) {
        Parser parser = Parser.builder().extensions(List.of(TablesExtension.create())).build();
        Document doc = (Document) parser.parse(markdown);
        VBox container = new VBox(8);
        container.setFillWidth(true);
        for (Node child = doc.getFirstChild(); child != null; child = child.getNext()) {
            javafx.scene.Node block = renderBlock(child, linkOpener);
            if (block instanceof Region r) r.setPadding(BLOCK_GAP);
            container.getChildren().add(block);
        }
        return container;
    }

    private static javafx.scene.Node renderBlock(Node node, Consumer<String> linkOpener) {
        if (node instanceof Heading h)        return renderHeading(h, linkOpener);
        if (node instanceof Paragraph p)      return inlineFlow(p, linkOpener);
        if (node instanceof BulletList bl)    return renderList(bl, false, linkOpener);
        if (node instanceof OrderedList ol)   return renderList(ol, true, linkOpener);
        if (node instanceof FencedCodeBlock f)return renderCodeBlock(f.getLiteral());
        if (node instanceof BlockQuote bq)    return renderBlockQuote(bq, linkOpener);
        if (node instanceof ThematicBreak)    return new Separator();
        if (node instanceof TableBlock tb)    return renderTable(tb, linkOpener);
        // Fallback: treat unknown block as inline content in a TextFlow.
        return inlineFlow(node, linkOpener);
    }

    private static Label renderHeading(Heading h, Consumer<String> linkOpener) {
        double size = switch (h.getLevel()) {
            case 1 -> H1_SIZE;
            case 2 -> H2_SIZE;
            case 3 -> H3_SIZE;
            default -> H4_SIZE;
        };
        Label label = new Label(plainText(h));
        label.setFont(Font.font(null, FontWeight.BOLD, size));
        label.setWrapText(true);
        label.setPadding(new Insets(h.getLevel() == 1 ? 4 : 8, 0, 4, 0));
        return label;
    }

    private static TextFlow inlineFlow(Node parent, Consumer<String> linkOpener) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(2);
        appendInline(flow, parent, linkOpener);
        return flow;
    }

    private static void appendInline(TextFlow flow, Node parent, Consumer<String> linkOpener) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof org.commonmark.node.Text t) {
                flow.getChildren().add(plain(t.getLiteral()));
            } else if (child instanceof StrongEmphasis) {
                Text node = plain(plainText(child));
                node.setFont(Font.font(null, FontWeight.BOLD, TEXT_SIZE));
                flow.getChildren().add(node);
            } else if (child instanceof Emphasis) {
                Text node = plain(plainText(child));
                node.setFont(Font.font(null, FontPosture.ITALIC, TEXT_SIZE));
                flow.getChildren().add(node);
            } else if (child instanceof Code c) {
                Text node = new Text(c.getLiteral());
                node.setFont(Font.font(CODE_FONT_FAMILY, CODE_SIZE));
                node.setStyle("-fx-fill: #1a3a5c;");
                flow.getChildren().add(node);
            } else if (child instanceof Link l) {
                Hyperlink link = new Hyperlink(plainText(l));
                link.setBorder(null);
                link.setPadding(new Insets(0));
                link.setOnAction(e -> linkOpener.accept(l.getDestination()));
                flow.getChildren().add(link);
            } else if (child instanceof HardLineBreak) {
                flow.getChildren().add(new Text("\n"));
            } else if (child instanceof SoftLineBreak) {
                flow.getChildren().add(new Text(" "));
            } else {
                appendInline(flow, child, linkOpener);
            }
        }
    }

    private static Text plain(String s) {
        Text t = new Text(s);
        t.setFont(Font.font(null, TEXT_SIZE));
        return t;
    }

    private static String plainText(Node node) {
        StringBuilder sb = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override public void visit(org.commonmark.node.Text text) { sb.append(text.getLiteral()); }
            @Override public void visit(SoftLineBreak n) { sb.append(' '); }
            @Override public void visit(HardLineBreak n) { sb.append('\n'); }
            @Override public void visit(Code n)          { sb.append(n.getLiteral()); }
        });
        return sb.toString();
    }

    private static VBox renderList(Node listNode, boolean ordered, Consumer<String> linkOpener) {
        VBox box = new VBox(4);
        int idx = 1;
        for (Node item = listNode.getFirstChild(); item != null; item = item.getNext()) {
            if (!(item instanceof ListItem)) continue;
            HBox row = new HBox(8);
            row.setAlignment(Pos.TOP_LEFT);
            Label marker = new Label(ordered ? (idx + ".") : "•");
            marker.setMinWidth(20);
            marker.setFont(Font.font(null, TEXT_SIZE));
            VBox itemBody = new VBox(4);
            HBox.setHgrow(itemBody, Priority.ALWAYS);
            for (Node child = item.getFirstChild(); child != null; child = child.getNext()) {
                itemBody.getChildren().add(renderBlock(child, linkOpener));
            }
            row.getChildren().addAll(marker, itemBody);
            box.getChildren().add(row);
            idx++;
        }
        return box;
    }

    private static Region renderCodeBlock(String code) {
        Label label = new Label(code.endsWith("\n") ? code.substring(0, code.length() - 1) : code);
        label.setFont(Font.font(CODE_FONT_FAMILY, CODE_SIZE));
        label.setStyle(
                "-fx-background-color: #f4f6f9;"
                + "-fx-text-fill: #1a3a5c;"
                + "-fx-border-color: #d6dde6;"
                + "-fx-border-radius: 4;"
                + "-fx-background-radius: 4;");
        label.setPadding(new Insets(8, 12, 8, 12));
        label.setWrapText(false);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static HBox renderBlockQuote(BlockQuote bq, Consumer<String> linkOpener) {
        VBox content = new VBox(6);
        for (Node child = bq.getFirstChild(); child != null; child = child.getNext()) {
            content.getChildren().add(renderBlock(child, linkOpener));
        }
        Region bar = new Region();
        bar.setPrefWidth(3);
        bar.setMinWidth(3);
        bar.setMaxWidth(3);
        bar.setStyle("-fx-background-color: #b6c4d4;");
        HBox box = new HBox(10, bar, content);
        HBox.setHgrow(content, Priority.ALWAYS);
        box.setPadding(new Insets(2, 0, 2, 0));
        return box;
    }

    private static GridPane renderTable(TableBlock table, Consumer<String> linkOpener) {
        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);
        grid.setStyle("-fx-border-color: #d6dde6; -fx-border-width: 1 0 0 1;");

        int rowIdx = 0;
        int columnCount = 0;
        for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
            boolean isHeader = section instanceof TableHead;
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                if (!(row instanceof TableRow)) continue;
                int colIdx = 0;
                for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                    if (!(cell instanceof TableCell tc)) continue;
                    TextFlow cellBody = inlineFlow(tc, linkOpener);
                    if (isHeader) {
                        for (javafx.scene.Node child : cellBody.getChildren()) {
                            if (child instanceof Text t) {
                                t.setFont(Font.font(null, FontWeight.BOLD, TEXT_SIZE));
                            }
                        }
                    }
                    Region cellWrap = new VBox(cellBody);
                    cellWrap.setPadding(new Insets(6, 10, 6, 10));
                    cellWrap.setStyle(
                            "-fx-border-color: #d6dde6;"
                            + "-fx-border-width: 0 1 1 0;"
                            + (isHeader ? "-fx-background-color: #f4f6f9;" : ""));
                    GridPane.setHgrow(cellWrap, Priority.ALWAYS);
                    GridPane.setHalignment(cellWrap, HPos.LEFT);
                    grid.add(cellWrap, colIdx, rowIdx);
                    colIdx++;
                }
                if (colIdx > columnCount) columnCount = colIdx;
                rowIdx++;
            }
            if (section instanceof TableBody) {
                // body comes after head; nothing to do here, just makes the structure explicit.
            }
        }
        for (int c = 0; c < columnCount; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.SOMETIMES);
            grid.getColumnConstraints().add(cc);
        }
        return grid;
    }
}
