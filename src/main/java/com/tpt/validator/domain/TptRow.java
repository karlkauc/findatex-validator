package com.tpt.validator.domain;

import com.tpt.validator.spec.FieldSpec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class TptRow {

    private final int rowIndex; // 1-based row index in input file (data row #1, #2, ...)
    private final Map<String, RawCell> cellsByNumKey = new LinkedHashMap<>();

    public TptRow(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int rowIndex() { return rowIndex; }

    public void put(String numKey, RawCell cell) {
        if (numKey == null || numKey.isBlank()) return;
        cellsByNumKey.put(numKey, cell);
    }

    public Optional<RawCell> get(String numKey) {
        return Optional.ofNullable(cellsByNumKey.get(numKey));
    }

    public Optional<RawCell> get(FieldSpec spec) {
        return get(spec.numKey());
    }

    public Optional<String> stringValue(String numKey) {
        return get(numKey).map(RawCell::trimmed).filter(s -> !s.isEmpty());
    }

    public Optional<String> stringValue(FieldSpec spec) {
        return stringValue(spec.numKey());
    }

    public Map<String, RawCell> all() { return cellsByNumKey; }

    public Optional<CicCode> cic() {
        return stringValue("12").flatMap(CicCode::parse);
    }
}
