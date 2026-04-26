package com.tpt.validator.spec;

import java.util.List;
import java.util.Optional;

public record CodificationDescriptor(
        CodificationKind kind,
        Optional<Integer> maxLength,
        List<ClosedListEntry> closedList,
        String rawText) {

    public boolean hasClosedList() {
        return closedList != null && !closedList.isEmpty();
    }

    public record ClosedListEntry(String code, String label) {
    }
}
