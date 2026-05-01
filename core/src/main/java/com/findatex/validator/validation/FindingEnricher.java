package com.findatex.validator.validation;

import com.findatex.validator.domain.FundGroup;
import com.findatex.validator.domain.FundGrouper;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Annotates each {@link Finding} with portfolio-level and (when row-scoped)
 * position-level identifying data so the report makes sense without needing
 * to cross-reference the source file. In multi-fund files the portfolio
 * context is resolved per fund group: the finding's row index is mapped back
 * to the group it belongs to.
 */
public final class FindingEnricher {

    private FindingEnricher() {}

    public static List<Finding> enrich(TptFile file, List<Finding> findings) {
        List<FundGroup> groups = FundGrouper.group(file);
        Map<Integer, FindingContext> portfolioByRow = new HashMap<>();
        for (FundGroup g : groups) {
            FindingContext pc = portfolioContextOf(g);
            for (TptRow r : g.rows()) portfolioByRow.put(r.rowIndex(), pc);
        }
        FindingContext fileFallback = groups.isEmpty()
                ? FindingContext.EMPTY
                : portfolioContextOf(groups.get(0));
        Map<Integer, TptRow> byRowIdx = indexRowsByRowIndex(file);

        List<Finding> out = new ArrayList<>(findings.size());
        for (Finding f : findings) {
            FindingContext base = (f.rowIndex() == null)
                    ? fileFallback
                    : portfolioByRow.getOrDefault(f.rowIndex(), fileFallback);
            FindingContext ctx = base;
            if (f.rowIndex() != null) {
                TptRow row = byRowIdx.get(f.rowIndex());
                if (row != null) {
                    ctx = base.withPosition(
                            row.stringValue("14").orElse(null),
                            row.stringValue("17").orElse(null),
                            row.stringValue("26").orElse(null));
                }
            }
            out.add(f.withContext(ctx));
        }
        return out;
    }

    private static FindingContext portfolioContextOf(FundGroup g) {
        String id   = firstNonEmpty(g, "1");
        String name = firstNonEmpty(g, "3");
        String date = firstNonEmpty(g, "6");
        return new FindingContext(id, name, date, null, null, null);
    }

    private static String firstNonEmpty(FundGroup g, String numKey) {
        for (TptRow r : g.rows()) {
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
