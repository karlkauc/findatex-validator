package com.findatex.validator.report;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.findatex.validator.validation.Finding;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

/**
 * Streams an {@link AnnotatedSourceModel} as gzip-compressed JSON for the web client's
 * "Annotated Source" view. Written once per validation run next to the XLSX report and
 * served verbatim (the file already <em>is</em> the wire format).
 *
 * <p>Contract (all indices 0-based):</p>
 * <pre>
 * { "headerRowIndex": 0,
 *   "headers": ["…", …],                  // width entries, "" when blank
 *   "columnsWithFindings": [0, 3, 14],     // mirror cols; 0 = row helper column
 *   "rows": [ { "r": logicalRow|null, "c": ["…", …] }, … ],
 *   "findingCells": [ [findingIndex, mirrorRow, mirrorCol], … ] }
 * </pre>
 * <ul>
 *   <li>{@code rows} holds <b>all</b> mirror rows in file order — {@code rows[k]} is mirror row
 *       {@code k}, including the header row and anything above it. The client hides
 *       {@code rows[headerRowIndex]} and uses {@code headers[]} as the column titles.
 *       {@code r} is the parsed logical row index or {@code null} for non-data rows.</li>
 *   <li>{@code c} holds exactly {@code width} cell texts ({@link AnnotatedSourceModel.Cell#text()}).
 *       Source column {@code j} is mirror column {@code j + 1}; the synthetic Row helper column
 *       {@code 0} is not part of {@code c}.</li>
 *   <li>{@code findingCells}: {@code findingIndex} is the position in the {@code findings} list
 *       handed to {@link #write} — for the web layer that is {@code ValidationResponse.findings[i]},
 *       so the client joins by index. {@code mirrorCol == 0} means row-level. Findings the model
 *       cannot locate (portfolio/global, rows never parsed) are omitted; indices of the remaining
 *       ones are <em>not</em> renumbered.</li>
 *   <li>No per-cell severity is carried — the client derives it from the joined findings.</li>
 * </ul>
 */
public final class AnnotatedSourceJson {

    private static final JsonFactory FACTORY = new JsonFactory();

    private AnnotatedSourceJson() {}

    /**
     * Whether a grid of {@code rows} × {@code width} is small enough to be materialised as a
     * JSON side artifact. Pure; overflow-safe.
     */
    public static boolean withinLimits(int rows, int width, int maxRows, long maxCells) {
        if (rows < 0 || width < 0) return false;
        if (rows > maxRows) return false;
        return (long) rows * (long) width <= maxCells;
    }

    /**
     * Writes the gzip-compressed JSON document to {@code out}. The stream is wrapped in a
     * {@link GZIPOutputStream} and closed on return.
     *
     * @param findings the finding list whose indices the client will join on — must be the
     *                 same list (same order) the caller exposes to the client
     */
    public static void write(AnnotatedSourceModel model, List<Finding> findings, OutputStream out)
            throws IOException {
        try (GZIPOutputStream gz = new GZIPOutputStream(out);
             JsonGenerator g = FACTORY.createGenerator(gz)) {
            g.writeStartObject();
            g.writeNumberField("headerRowIndex", model.headerRowIndex());

            g.writeArrayFieldStart("headers");
            for (String h : model.headers()) g.writeString(h == null ? "" : h);
            g.writeEndArray();

            g.writeArrayFieldStart("columnsWithFindings");
            for (int c : model.columnsWithFindings()) g.writeNumber(c);
            g.writeEndArray();

            g.writeArrayFieldStart("rows");
            for (AnnotatedSourceModel.Row row : model.rows()) {
                g.writeStartObject();
                if (row.logicalRow() == null) {
                    g.writeNullField("r");
                } else {
                    g.writeNumberField("r", row.logicalRow());
                }
                g.writeArrayFieldStart("c");
                for (AnnotatedSourceModel.Cell cell : row.cells()) {
                    String text = cell.text();
                    g.writeString(text == null ? "" : text);
                }
                g.writeEndArray();
                g.writeEndObject();
            }
            g.writeEndArray();

            g.writeArrayFieldStart("findingCells");
            for (int i = 0; i < findings.size(); i++) {
                Optional<AnnotatedSourceModel.CellRef> ref = model.locate(findings.get(i));
                if (ref.isEmpty()) continue;
                g.writeStartArray();
                g.writeNumber(i);
                g.writeNumber(ref.get().mirrorRow());
                g.writeNumber(ref.get().mirrorCol());
                g.writeEndArray();
            }
            g.writeEndArray();

            g.writeEndObject();
        }
    }
}
