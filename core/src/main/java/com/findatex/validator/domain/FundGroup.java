package com.findatex.validator.domain;

import java.util.List;

/**
 * Consecutive slice of {@link TptRow}s belonging to the same fund.
 * Produced by {@link FundGrouper#group(TptFile)}.
 */
public record FundGroup(FundKey key, int firstRowIndex, int lastRowIndex, List<TptRow> rows) {

    public FundGroup {
        rows = List.copyOf(rows);
    }
}
