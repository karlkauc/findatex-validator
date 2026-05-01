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
    /** Original file bytes when loaded from a stream (web upload); {@code null} when loaded
     *  from a real on-disk path (JavaFX desktop). Lets the report writer rebuild the
     *  Annotated-Source tab without reaching back to a path that no longer exists. */
    private final byte[] sourceBytes;

    public TptFile(Path source,
                   String inputFormat,
                   List<String> rawHeaders,
                   Map<Integer, String> headerToNumKey,
                   List<String> unmappedHeaders,
                   List<TptRow> rows) {
        this(source, inputFormat, rawHeaders, headerToNumKey, unmappedHeaders, rows, null);
    }

    public TptFile(Path source,
                   String inputFormat,
                   List<String> rawHeaders,
                   Map<Integer, String> headerToNumKey,
                   List<String> unmappedHeaders,
                   List<TptRow> rows,
                   byte[] sourceBytes) {
        this.source = source;
        this.inputFormat = inputFormat;
        this.rawHeaders = List.copyOf(rawHeaders);
        this.headerToNumKey = Collections.unmodifiableMap(new LinkedHashMap<>(headerToNumKey));
        this.unmappedHeaders = List.copyOf(unmappedHeaders);
        this.rows = List.copyOf(rows);
        this.sourceBytes = sourceBytes;
    }

    public Path source() { return source; }
    public String inputFormat() { return inputFormat; }
    public List<String> rawHeaders() { return rawHeaders; }
    public Map<Integer, String> headerToNumKey() { return headerToNumKey; }
    public List<String> unmappedHeaders() { return unmappedHeaders; }
    public List<TptRow> rows() { return rows; }
    /** Original file bytes captured at load time when no on-disk source is available. */
    public byte[] sourceBytes() { return sourceBytes; }

    /** Convenience: numKeys present in this file (i.e. mapped headers). */
    public List<String> presentNumKeys() {
        return new ArrayList<>(headerToNumKey.values());
    }
}
