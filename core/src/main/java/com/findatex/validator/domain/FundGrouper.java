package com.findatex.validator.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a {@link TptFile}'s rows into consecutive {@link FundGroup}s.
 * Boundary detection is driven by the template-declared portfolio-id field —
 * a new group starts when that field changes to a different non-empty value.
 * Empty portfolio-id rows inherit the current group's identity (continuation
 * rows are common in real-world TPT extracts where the field is set only on
 * the first row of each fund). Each group's {@link FundKey} carries the
 * portfolio id together with the first non-empty valuation-date field
 * encountered within that group, so a malformed value on a single row does
 * not spuriously split the fund.
 *
 * <p>Templates without a portfolio dimension (e.g. EET/EPT in their current shape) pass
 * {@code null} for both keys; the file is then treated as a single group.</p>
 */
public final class FundGrouper {

    private FundGrouper() {}

    /** Convenience overload preserving the historic TPT keys (portfolio id = NUM 1, date = NUM 6). */
    public static List<FundGroup> group(TptFile file) {
        return group(file, "1", "6");
    }

    public static List<FundGroup> group(TptFile file, String portfolioIdNumKey, String valuationDateNumKey) {
        List<TptRow> rows = file.rows();
        if (rows.isEmpty()) return List.of();

        if (portfolioIdNumKey == null) {
            // Single group covering the whole file.
            int first = rows.get(0).rowIndex();
            int last = rows.get(rows.size() - 1).rowIndex();
            return List.of(new FundGroup(new FundKey(null,
                    valuationDateNumKey == null ? null : firstNonEmpty(rows, valuationDateNumKey)),
                    first, last, new ArrayList<>(rows)));
        }

        List<FundGroup> out = new ArrayList<>();
        String currentPid = null;
        List<TptRow> currentRows = new ArrayList<>();
        int firstRowIndex = -1;
        int lastRowIndex = -1;

        for (TptRow row : rows) {
            String pid = row.stringValue(portfolioIdNumKey).orElse(null);
            boolean newGroup = currentRows.isEmpty()
                    || (pid != null && !pid.equals(currentPid));

            if (newGroup && !currentRows.isEmpty()) {
                out.add(new FundGroup(keyFor(currentPid, currentRows, valuationDateNumKey),
                        firstRowIndex, lastRowIndex, currentRows));
                currentRows = new ArrayList<>();
            }
            if (newGroup) {
                currentPid = pid;
                firstRowIndex = row.rowIndex();
            }
            currentRows.add(row);
            lastRowIndex = row.rowIndex();
        }

        if (!currentRows.isEmpty()) {
            out.add(new FundGroup(keyFor(currentPid, currentRows, valuationDateNumKey),
                    firstRowIndex, lastRowIndex, currentRows));
        }
        return out;
    }

    private static FundKey keyFor(String portfolioId, List<TptRow> rows, String valuationDateNumKey) {
        return new FundKey(portfolioId,
                valuationDateNumKey == null ? null : firstNonEmpty(rows, valuationDateNumKey));
    }

    private static String firstNonEmpty(List<TptRow> rows, String numKey) {
        for (TptRow r : rows) {
            String v = r.stringValue(numKey).orElse(null);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }
}
