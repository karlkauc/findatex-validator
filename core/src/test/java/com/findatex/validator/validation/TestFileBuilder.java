package com.findatex.validator.validation;

import com.findatex.validator.domain.RawCell;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny in-memory {@link TptFile} builder for unit tests. */
public final class TestFileBuilder {

    private final List<TptRow> rows = new ArrayList<>();
    private final Map<Integer, String> headerToNumKey = new LinkedHashMap<>();
    private final List<String> headers = new ArrayList<>();

    public TestFileBuilder header(String numKey) {
        headerToNumKey.put(headers.size(), numKey);
        headers.add(numKey);
        return this;
    }

    public TestFileBuilder row(Map<String, String> values) {
        TptRow r = new TptRow(rows.size() + 1);
        for (Map.Entry<String, String> e : values.entrySet()) {
            r.put(e.getKey(), new RawCell(e.getValue(), rows.size() + 2, 1));
            // implicitly register the header so the file knows it has this column
            if (!headerToNumKey.containsValue(e.getKey())) {
                headerToNumKey.put(headers.size(), e.getKey());
                headers.add(e.getKey());
            }
        }
        rows.add(r);
        return this;
    }

    public TptFile build() {
        return new TptFile(Path.of("/test/in-memory.csv"), "csv", headers, headerToNumKey, List.of(), rows);
    }

    public static Map<String, String> values(Object... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], String.valueOf(kv[i + 1]));
        return m;
    }
}
