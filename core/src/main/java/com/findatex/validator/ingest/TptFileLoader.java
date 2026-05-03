package com.findatex.validator.ingest;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.SpecCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * <p><b>TODO — streaming ingest refactor.</b> Both {@link XlsxLoader} and
 * {@link CsvLoader} currently call {@code in.readAllBytes()} on the {@code
 * load(InputStream, String)} entry point, which holds the entire upload
 * resident in a single byte[]. With the web layer's 25 MB body cap and four
 * concurrent validations in flight, peak resident bytes per request are
 * ~3× the upload (raw byte[] + POI's parse buffers + the optional
 * {@code TptFile.sourceBytes} kept for the Annotated-Source report tab).
 * A streaming refactor would: (a) hand POI the underlying multipart {@code
 * InputStream} directly, (b) retain {@code sourceBytes} only when the caller
 * has actually requested annotated-source output. POI's hardening limits
 * configured in {@code TemplateRegistry.init()} bound the worst case in the
 * meantime. Tracked separately — non-trivial because both UI surfaces and the
 * report writer assume the in-memory byte[] is available.
 */
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
