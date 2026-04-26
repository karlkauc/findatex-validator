package com.tpt.validator.spec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SpecCatalog {

    private final List<FieldSpec> fields;
    private final Map<String, FieldSpec> byNumKey;
    private final Map<String, FieldSpec> byNumData;
    private final Map<String, FieldSpec> byPath;

    public SpecCatalog(List<FieldSpec> fields) {
        this.fields = List.copyOf(fields);
        this.byNumKey = new LinkedHashMap<>();
        this.byNumData = new LinkedHashMap<>();
        this.byPath = new LinkedHashMap<>();
        for (FieldSpec f : fields) {
            byNumKey.putIfAbsent(f.numKey(), f);
            byNumData.putIfAbsent(normalize(f.numData()), f);
            if (f.fundXmlPath() != null && !f.fundXmlPath().isBlank()) {
                byPath.putIfAbsent(normalizePath(f.fundXmlPath()), f);
            }
        }
    }

    public List<FieldSpec> fields() { return fields; }

    public Optional<FieldSpec> byNumKey(String key) {
        return Optional.ofNullable(byNumKey.get(key));
    }

    public Optional<FieldSpec> byNumData(String numData) {
        return Optional.ofNullable(byNumData.get(normalize(numData)));
    }

    public Optional<FieldSpec> byPath(String path) {
        return Optional.ofNullable(byPath.get(normalizePath(path)));
    }

    /** Header name match: try numKey, numData, FunDataXML path. */
    public Optional<FieldSpec> matchHeader(String header) {
        if (header == null || header.isBlank()) return Optional.empty();
        String h = header.trim();

        // Pure num token? (e.g. "12", "8b")
        Optional<FieldSpec> n = byNumKey(h);
        if (n.isPresent()) return n;

        // Numbered prefix like "12_..." → take the numKey.
        int us = h.indexOf('_');
        if (us > 0) {
            Optional<FieldSpec> p = byNumKey(h.substring(0, us));
            if (p.isPresent()) return p;
        }

        Optional<FieldSpec> nd = byNumData(h);
        if (nd.isPresent()) return nd;

        Optional<FieldSpec> bp = byPath(h);
        if (bp.isPresent()) return bp;
        return Optional.empty();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replace(" ", " ");
    }

    private static String normalizePath(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "").toLowerCase();
    }
}
