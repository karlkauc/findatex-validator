package com.findatex.validator.spec;

public enum Flag {
    M, C, O, I, NA, UNKNOWN;

    public static Flag parse(String raw) {
        if (raw == null) return UNKNOWN;
        String s = raw.trim().toUpperCase();
        if (s.isEmpty()) return UNKNOWN;
        return switch (s) {
            case "M" -> M;
            case "C" -> C;
            case "O" -> O;
            case "I" -> I;
            case "N/A", "NA" -> NA;
            default -> UNKNOWN;
        };
    }
}
