package com.findatex.validator.ingest;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.SpecCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;

public final class TptFileLoader {

    private final XlsxLoader xlsxLoader;
    private final CsvLoader csvLoader;

    public TptFileLoader(SpecCatalog catalog) {
        this.xlsxLoader = new XlsxLoader(catalog);
        this.csvLoader = new CsvLoader(catalog);
    }

    public TptFile load(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (isXlsx(name)) return xlsxLoader.load(file);
        if (isCsv(name)) return csvLoader.load(file);
        throw new IOException("Unsupported file extension: " + file.getFileName());
    }

    /**
     * Stream-based loader used by the web upload path. {@code filename} is consulted only
     * for format detection (.xlsx vs .csv) and ends up as the synthetic source path on the
     * resulting {@link TptFile}.
     */
    public TptFile load(InputStream in, String filename) throws IOException {
        String name = (filename == null ? "" : filename).toLowerCase(Locale.ROOT);
        if (isXlsx(name)) return xlsxLoader.load(in, filename);
        if (isCsv(name)) return csvLoader.load(in, filename);
        throw new IOException("Unsupported file extension: " + filename);
    }

    private static boolean isXlsx(String name) {
        return name.endsWith(".xlsx") || name.endsWith(".xlsm");
    }

    private static boolean isCsv(String name) {
        return name.endsWith(".csv") || name.endsWith(".tsv") || name.endsWith(".txt");
    }
}
