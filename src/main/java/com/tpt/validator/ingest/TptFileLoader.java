package com.tpt.validator.ingest;

import com.tpt.validator.domain.TptFile;
import com.tpt.validator.spec.SpecCatalog;

import java.io.IOException;
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
        if (name.endsWith(".xlsx") || name.endsWith(".xlsm")) return xlsxLoader.load(file);
        if (name.endsWith(".csv") || name.endsWith(".tsv") || name.endsWith(".txt")) return csvLoader.load(file);
        throw new IOException("Unsupported file extension: " + file.getFileName());
    }
}
