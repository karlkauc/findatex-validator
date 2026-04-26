package com.tpt.validator.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Annex V CIC code: 4 chars = country (2) + category digit (1) + subcategory alphanum (1).
 * Special country values: XL (off-shore international), XV (other), XT (supranational).
 */
public final class CicCode {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z]{2}[0-9A-Fa-f][0-9A-Za-z]$");

    private final String raw;
    private final String countryCode;
    private final String categoryDigit;
    private final String subcategory;

    private CicCode(String raw, String country, String category, String sub) {
        this.raw = raw;
        this.countryCode = country;
        this.categoryDigit = category;
        this.subcategory = sub;
    }

    public static Optional<CicCode> parse(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.trim();
        if (s.length() != 4 || !PATTERN.matcher(s).matches()) return Optional.empty();
        return Optional.of(new CicCode(
                s.toUpperCase(),
                s.substring(0, 2).toUpperCase(),
                s.substring(2, 3).toUpperCase(),
                s.substring(3, 4).toUpperCase()));
    }

    public String raw() { return raw; }
    public String countryCode() { return countryCode; }
    public String categoryDigit() { return categoryDigit; }
    public String subcategory() { return subcategory; }

    /** Map the category digit to the human-readable group used in the Solvency II categorisation. */
    public String categoryName() {
        return switch (categoryDigit) {
            case "0" -> "Other (CIC 0)";
            case "1" -> "Government bonds (CIC 1)";
            case "2" -> "Corporate bonds (CIC 2)";
            case "3" -> "Equity (CIC 3)";
            case "4" -> "Collective investment undertakings (CIC 4)";
            case "5" -> "Structured notes (CIC 5)";
            case "6" -> "Collateralized securities (CIC 6)";
            case "7" -> "Cash & deposits (CIC 7)";
            case "8" -> "Mortgages/Loans (CIC 8)";
            case "9" -> "Property (CIC 9)";
            case "A" -> "Futures (CIC A)";
            case "B" -> "Call options (CIC B)";
            case "C" -> "Put options (CIC C)";
            case "D" -> "Swaps (CIC D)";
            case "E" -> "Forwards (CIC E)";
            case "F" -> "Credit derivatives (CIC F)";
            default  -> "Unknown CIC";
        };
    }

    @Override public String toString() { return raw; }
}
