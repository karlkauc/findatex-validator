package com.findatex.validator.domain;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TptFile {

    private final Path source;
    private final String inputFormat; // "xlsx" | "csv"
    private final List<String> rawHeaders;
    private final Map<Integer, String> headerToNumKey;     // input col idx -> numKey
    private final List<String> unmappedHeaders;
    private final List<TptRow> rows;

    public TptFile(Path source,
                   String inputFormat,
                   List<String> rawHeaders,
                   Map<Integer, String> headerToNumKey,
                   List<String> unmappedHeaders,
                   List<TptRow> rows) {
        this.source = source;
        this.inputFormat = inputFormat;
        this.rawHeaders = List.copyOf(rawHeaders);
        this.headerToNumKey = Collections.unmodifiableMap(new LinkedHashMap<>(headerToNumKey));
        this.unmappedHeaders = List.copyOf(unmappedHeaders);
        this.rows = List.copyOf(rows);
    }

    public Path source() { return source; }
    public String inputFormat() { return inputFormat; }
    public List<String> rawHeaders() { return rawHeaders; }
    public Map<Integer, String> headerToNumKey() { return headerToNumKey; }
    public List<String> unmappedHeaders() { return unmappedHeaders; }
    public List<TptRow> rows() { return rows; }

    /** Convenience: numKeys present in this file (i.e. mapped headers). */
    public List<String> presentNumKeys() {
        return new ArrayList<>(headerToNumKey.values());
    }
}
