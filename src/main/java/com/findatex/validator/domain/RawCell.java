package com.findatex.validator.domain;

public record RawCell(String value, int sourceRow, int sourceCol) {

    public boolean isEmpty() {
        return value == null || value.trim().isEmpty();
    }

    public String trimmed() {
        return value == null ? "" : value.trim();
    }
}
