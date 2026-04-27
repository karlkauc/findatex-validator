package com.findatex.validator.validation;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Annotates each {@link Finding} with portfolio-level and (when row-scoped)
 * position-level identifying data so the report makes sense without needing
 * to cross-reference the source file.
 */
public final class FindingEnricher {

    private FindingEnricher() {}

    public static List<Finding> enrich(TptFile file, List<Finding> findings) {
        FindingContext portfolio = portfolioContext(file);
        Map<Integer, TptRow> byRowIdx = indexRowsByRowIndex(file);

        List<Finding> out = new ArrayList<>(findings.size());
        for (Finding f : findings) {
            FindingContext ctx = portfolio;
            if (f.rowIndex() != null) {
                TptRow row = byRowIdx.get(f.rowIndex());
                if (row != null) {
                    ctx = portfolio.withPosition(
                            row.stringValue("14").orElse(null),
                            row.stringValue("17").orElse(null),
                            row.stringValue("26").orElse(null));
                }
            }
            out.add(f.withContext(ctx));
        }
        return out;
    }

    private static FindingContext portfolioContext(TptFile file) {
        // Portfolio-level fields are constant across rows; pick the first non-empty.
        String id   = firstNonEmpty(file, "1");
        String name = firstNonEmpty(file, "3");
        String date = firstNonEmpty(file, "6");
        return new FindingContext(id, name, date, null, null, null);
    }

    private static String firstNonEmpty(TptFile file, String numKey) {
        for (TptRow r : file.rows()) {
            String v = r.stringValue(numKey).orElse(null);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static Map<Integer, TptRow> indexRowsByRowIndex(TptFile file) {
        Map<Integer, TptRow> map = new HashMap<>();
        for (TptRow r : file.rows()) map.put(r.rowIndex(), r);
        return map;
    }
}
