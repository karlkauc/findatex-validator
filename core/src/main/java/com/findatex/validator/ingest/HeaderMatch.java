package com.findatex.validator.ingest;

import com.findatex.validator.domain.TptFile;

import java.util.ArrayList;
import java.util.List;

/**
 * How much of a loaded file's header row the chosen template recognised. A file whose
 * headers are (almost) all unknown is not a poor delivery but the wrong template, a
 * missing header row or a misread layout — validating it would only produce one
 * "missing" finding per row and mandatory field.
 *
 * @param headers      non-blank header cells
 * @param recognised   header cells mapped to a field of the template
 * @param firstHeaders the first few header cells as read, shortened for display
 */
public record HeaderMatch(int headers, int recognised, List<String> firstHeaders) {

    private static final int MIN_RECOGNISED = 3;
    private static final int NARROW_FILE = 5;
    private static final int SHOWN_HEADERS = 3;
    private static final int SHOWN_CHARS = 40;

    public static HeaderMatch of(TptFile file) {
        int headers = 0;
        List<String> first = new ArrayList<>();
        for (String h : file.rawHeaders()) {
            if (h == null || h.isBlank()) continue;
            headers++;
            if (first.size() < SHOWN_HEADERS) {
                first.add(h.length() > SHOWN_CHARS ? h.substring(0, SHOWN_CHARS) + "…" : h);
            }
        }
        return new HeaderMatch(headers, file.headerToNumKey().size(), List.copyOf(first));
    }

    /**
     * No header recognised, or just one or two in a file with more than {@value #NARROW_FILE}
     * columns. Deliberately not a ratio: real deliveries with many non-standard column names
     * must still be validated.
     */
    public boolean mismatch() {
        return headers > 0 && (recognised == 0 || (recognised < MIN_RECOGNISED && headers > NARROW_FILE));
    }

    /**
     * @param template      what the file was checked against, e.g. {@code TPT V8.0}
     * @param exampleHeader a column name of that template, shown as the expected spelling
     */
    public String describe(String template, String exampleHeader) {
        StringBuilder sb = new StringBuilder("File does not match template ").append(template).append(": ");
        if (recognised == 0) {
            sb.append("none of the ").append(headers).append(" column header(s) are recognized.");
        } else {
            sb.append("only ").append(recognised).append(" of the ").append(headers)
                    .append(" column header(s) are recognized.");
        }
        sb.append(" First header(s) read: ");
        for (int i = 0; i < firstHeaders.size(); i++) {
            sb.append(i == 0 ? "\"" : ", \"").append(firstHeaders.get(i)).append('"');
        }
        sb.append(". Expected column names like \"").append(exampleHeader).append("\".")
                .append(" If the headers above are not what the file's header row contains, check that")
                .append(" the header row is within the first 50 lines and that the columns are separated")
                .append(" by ; , | or tab. Otherwise check that you picked the right template and version.");
        return sb.toString();
    }
}
