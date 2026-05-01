package com.findatex.validator.validation;

import com.findatex.validator.domain.FundGroup;
import com.findatex.validator.domain.FundGrouper;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.template.api.FindingContextSpec;
import com.findatex.validator.template.tpt.TptTemplate;

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
 *
 * <p>The mapping from context slots (portfolio id/name, valuation date, instrument
 * code/name/weight) to data-file field {@code numKey}s is template-specific and supplied via
 * {@link FindingContextSpec}. Templates without a portfolio dimension supply
 * {@link FindingContextSpec#EMPTY}; findings then carry no portfolio/position context.</p>
 */
public final class FindingEnricher {

    private FindingEnricher() {}

    /**
     * Backwards-compatible TPT-shaped enrichment.
     * @deprecated Pass an explicit {@link FindingContextSpec} so non-TPT findings get the right
     *             field mapping. Kept for tests and existing call sites that still default to TPT.
     */
    @Deprecated
    public static List<Finding> enrich(TptFile file, List<Finding> findings) {
        return enrich(file, findings, TptTemplate.FINDING_CONTEXT);
    }

    public static List<Finding> enrich(TptFile file, List<Finding> findings, FindingContextSpec spec) {
        if (spec == null) spec = FindingContextSpec.EMPTY;
        List<FundGroup> groups = FundGrouper.group(file,
                spec.portfolioIdNumKey(), spec.valuationDateNumKey());
        Map<Integer, FindingContext> portfolioByRow = new HashMap<>();
        for (FundGroup g : groups) {
            FindingContext pc = portfolioContextOf(g, spec);
            for (TptRow r : g.rows()) portfolioByRow.put(r.rowIndex(), pc);
        }
        FindingContext fileFallback = groups.isEmpty()
                ? FindingContext.EMPTY
                : portfolioContextOf(groups.get(0), spec);
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
                            valueAt(row, spec.instrumentCodeNumKey()),
                            valueAt(row, spec.instrumentNameNumKey()),
                            valueAt(row, spec.valuationWeightNumKey()));
                }
            }
            out.add(f.withContext(ctx));
        }
        return out;
    }

    private static FindingContext portfolioContextOf(FundGroup g, FindingContextSpec spec) {
        String id   = firstNonEmpty(g, spec.portfolioIdNumKey());
        String name = firstNonEmpty(g, spec.portfolioNameNumKey());
        String date = firstNonEmpty(g, spec.valuationDateNumKey());
        return new FindingContext(id, name, date, null, null, null);
    }

    private static String valueAt(TptRow row, String numKey) {
        return numKey == null ? null : row.stringValue(numKey).orElse(null);
    }

    private static String firstNonEmpty(FundGroup g, String numKey) {
        if (numKey == null) return null;
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
