package com.findatex.validator.ui;

import com.findatex.validator.validation.Severity;

/**
 * Toolkit-free helpers behind {@link AnnotatedSourcePane}: column titles, severity → CSS class
 * and the grid-size summary. Kept out of the Node subclass so they can be unit-tested without
 * bringing up JavaFX.
 */
final class AnnotatedSourceColumns {

    private AnnotatedSourceColumns() {}

    /** Header text when present, otherwise the spreadsheet letter for the 0-based source column. */
    static String title(int sourceCol, String header) {
        if (header != null && !header.isBlank()) return header.trim();
        return letters(sourceCol);
    }

    /** 0 → A, 25 → Z, 26 → AA … like a spreadsheet column label. */
    static String letters(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    static String styleClassFor(Severity severity) {
        if (severity == null) return null;
        return switch (severity) {
            case ERROR -> "source-cell-error";
            case WARNING -> "source-cell-warn";
            case INFO -> "source-cell-info";
        };
    }

    static String summary(int rows, int columns, int rowsWithFindings) {
        return rows + " rows × " + columns + " columns, " + rowsWithFindings + " rows with findings";
    }
}
