package com.tpt.validator.ingest;

import com.tpt.validator.spec.FieldSpec;
import com.tpt.validator.spec.SpecCatalog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class HeaderMapper {

    private final SpecCatalog catalog;

    public HeaderMapper(SpecCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Map a list of header strings to a map of {column index → numKey}.
     * Unmapped headers are accumulated in {@code unmapped}.
     */
    public Map<Integer, String> map(java.util.List<String> headers, java.util.List<String> unmapped) {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (h == null || h.isBlank()) continue;
            Optional<FieldSpec> match = catalog.matchHeader(h);
            if (match.isPresent()) {
                out.put(i, match.get().numKey());
            } else {
                unmapped.add(h);
            }
        }
        return out;
    }
}
