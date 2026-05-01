package com.findatex.validator.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a {@link TptFile}'s rows into consecutive {@link FundGroup}s.
 * Boundary detection is driven by field 1 (Portfolio_identifying_data) only —
 * a new group starts when field 1 changes to a different non-empty value.
 * Empty field-1 rows inherit the current group's identity (continuation rows
 * are common in real-world TPT extracts where field 1 is set only on the
 * first row of each fund). Each group's {@link FundKey} carries the
 * portfolio id together with the first non-empty field 6 (Valuation_date)
 * encountered within that group, so a malformed value on a single row does
 * not spuriously split the fund.
 */
public final class FundGrouper {

    private FundGrouper() {}

    public static List<FundGroup> group(TptFile file) {
        List<TptRow> rows = file.rows();
        if (rows.isEmpty()) return List.of();

        List<FundGroup> out = new ArrayList<>();
        String currentPid = null;
        List<TptRow> currentRows = new ArrayList<>();
        int firstRowIndex = -1;
        int lastRowIndex = -1;

        for (TptRow row : rows) {
            String pid = row.stringValue("1").orElse(null);
            boolean newGroup = currentRows.isEmpty()
                    || (pid != null && !pid.equals(currentPid));

            if (newGroup && !currentRows.isEmpty()) {
                out.add(new FundGroup(keyFor(currentPid, currentRows),
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
            out.add(new FundGroup(keyFor(currentPid, currentRows),
                    firstRowIndex, lastRowIndex, currentRows));
        }
        return out;
    }

    private static FundKey keyFor(String portfolioId, List<TptRow> rows) {
        String date = null;
        for (TptRow r : rows) {
            String v = r.stringValue("6").orElse(null);
            if (v != null && !v.isEmpty()) { date = v; break; }
        }
        return new FundKey(portfolioId, date);
    }
}
