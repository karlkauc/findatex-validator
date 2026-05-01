package com.findatex.validator.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodificationParser {

    /**
     * Closed-list bullet line. Two branches:
     * <ul>
     *   <li>Digit-led code (e.g. {@code 1}, {@code 3L}) followed by {@code -}, {@code –} or {@code .}.</li>
     *   <li>Pure-letter code (e.g. {@code A}, {@code L}) followed only by {@code -} or {@code –}.
     *       Period is intentionally excluded for letter-led codes; otherwise narrative bullets
     *       like {@code e.g. "BLOOMBERG"...} would be misparsed as a closed-list entry with
     *       code {@code e}.</li>
     * </ul>
     * The label after the separator must start with whitespace — that's what distinguishes
     * a real bullet ({@code "1- option A"}) from inline example text such as a decimal number
     * inside a sentence ({@code "Floating decimal. 1.15% = 0.0115"}).
     * Group 1 captures a digit-led code, group 2 captures a pure-letter code, group 3 is the label.
     */
    private static final Pattern CLOSED_LIST_LINE =
            Pattern.compile("^\\s*(?:(\\d+[a-zA-Z]?)\\s*[-–.]|([A-Za-z])\\s*[-–])\\s+(.+)$", Pattern.MULTILINE);
    /** Quoted token list, e.g. {@code "Bullet", "Sinkable"} or {@code "Y" ; "N"; "EPM"}. */
    private static final Pattern QUOTED_TOKEN =
            Pattern.compile("\"([A-Za-z0-9 _-]{1,40})\"");
    private static final Pattern ALPHANUM_PATTERN =
            Pattern.compile("alphanum(?:eric)?\\s*\\(?\\s*(?:max\\s*)?(\\d+)\\s*\\)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALPHA_PATTERN =
            Pattern.compile("alpha\\s*\\(?\\s*(\\d+)\\s*\\)?", Pattern.CASE_INSENSITIVE);
    /** Whole-string numeric range, e.g. {@code "1-7"}, {@code "1-7 or Empty"}, {@code "1 – 6"}. */
    private static final Pattern NUMERIC_RANGE_PATTERN =
            Pattern.compile("^\\s*(\\d+)\\s*[-–]\\s*(\\d+)\\s*(?:or\\s+empty)?\\s*$",
                    Pattern.CASE_INSENSITIVE);
    /** Single-uppercase-letter code introduced by an {@code "or"} keyword (case-insensitive
     *  {@code or}, but the captured letter must be uppercase and not part of a longer word).
     *  Used for mixed numeric+letter codifications like {@code "floating decimal or V or S or M or L or H"}. */
    private static final Pattern OR_SINGLE_LETTER =
            Pattern.compile("\\b[oO][rR]\\s+([A-Z])(?![a-zA-Z])");

    private CodificationParser() {}

    public static CodificationDescriptor parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new CodificationDescriptor(CodificationKind.UNKNOWN, Optional.empty(), List.of(), "");
        }
        String text = raw.trim();
        String lower = text.toLowerCase();

        // EMT-style numeric ranges like "1-7 or Empty" describe a discrete set of integer codes.
        // Match them before the bullet-style closed-list regex so we don't misread the upper
        // bound as the label of a single-entry closed list ("1" / "7 or Empty").
        Matcher rangeMatcher = NUMERIC_RANGE_PATTERN.matcher(text);
        if (rangeMatcher.matches()) {
            int lo = Integer.parseInt(rangeMatcher.group(1));
            int hi = Integer.parseInt(rangeMatcher.group(2));
            if (lo <= hi) {
                List<CodificationDescriptor.ClosedListEntry> entries = new ArrayList<>(hi - lo + 1);
                for (int i = lo; i <= hi; i++) {
                    String code = Integer.toString(i);
                    entries.add(new CodificationDescriptor.ClosedListEntry(code, code));
                }
                return new CodificationDescriptor(CodificationKind.CLOSED_LIST, Optional.empty(), entries, text);
            }
        }

        List<CodificationDescriptor.ClosedListEntry> closedList = parseClosedList(text);

        // Order matters: special types first.
        if (lower.contains("iso 4217")) {
            return new CodificationDescriptor(CodificationKind.ISO_4217, Optional.empty(), closedList, text);
        }
        if (lower.contains("iso 3166")) {
            return new CodificationDescriptor(CodificationKind.ISO_3166_A2, Optional.empty(), closedList, text);
        }
        if (lower.contains("yyyy-mm-dd") || lower.contains("iso 8601")) {
            return new CodificationDescriptor(CodificationKind.DATE, Optional.empty(), closedList, text);
        }
        if (lower.contains("nace")) {
            return new CodificationDescriptor(CodificationKind.NACE, Optional.empty(), closedList, text);
        }
        if (lower.contains("cic code") || lower.contains("alphanumeric (4)")) {
            return new CodificationDescriptor(CodificationKind.CIC, Optional.of(4), closedList, text);
        }
        // Closed list takes precedence over generic numeric/alpha when codes are enumerated.
        if (!closedList.isEmpty()) {
            return new CodificationDescriptor(CodificationKind.CLOSED_LIST, Optional.empty(), closedList, text);
        }
        if (lower.contains("floating decimal") || lower.startsWith("number")) {
            // Some EMT cost/period fields allow EITHER a number OR specific letter codes —
            // e.g. NUM 55 ("floating decimal or V or S or M or L or H"). Capture those letters
            // so FormatRule can accept them as alternatives to a numeric value.
            List<CodificationDescriptor.ClosedListEntry> alternatives = extractOrLetterCodes(text);
            return new CodificationDescriptor(
                    CodificationKind.NUMERIC,
                    Optional.empty(),
                    alternatives.isEmpty() ? closedList : alternatives,
                    text);
        }
        Matcher anm = ALPHANUM_PATTERN.matcher(text);
        if (anm.find()) {
            return new CodificationDescriptor(
                    CodificationKind.ALPHANUMERIC,
                    Optional.of(Integer.parseInt(anm.group(1))),
                    closedList,
                    text);
        }
        Matcher am = ALPHA_PATTERN.matcher(text);
        if (am.find()) {
            return new CodificationDescriptor(
                    CodificationKind.ALPHA,
                    Optional.of(Integer.parseInt(am.group(1))),
                    closedList,
                    text);
        }
        return new CodificationDescriptor(CodificationKind.FREE_TEXT, Optional.empty(), closedList, text);
    }

    private static List<CodificationDescriptor.ClosedListEntry> extractOrLetterCodes(String text) {
        Matcher m = OR_SINGLE_LETTER.matcher(text);
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        while (m.find()) seen.add(m.group(1));
        List<CodificationDescriptor.ClosedListEntry> out = new ArrayList<>(seen.size());
        for (String code : seen) out.add(new CodificationDescriptor.ClosedListEntry(code, code));
        return out;
    }

    private static List<CodificationDescriptor.ClosedListEntry> parseClosedList(String text) {
        List<CodificationDescriptor.ClosedListEntry> entries = new ArrayList<>();
        Matcher m = CLOSED_LIST_LINE.matcher(text);
        while (m.find()) {
            String code = (m.group(1) != null ? m.group(1) : m.group(2)).trim();
            String label = m.group(3).trim();
            entries.add(new CodificationDescriptor.ClosedListEntry(code, label));
        }
        // The regex also matches free-text bullets like "1. ISO 6166". To reduce false
        // positives, drop entries when the same field's label looks like a sentence (>120 chars
        // or contains a period followed by a capital letter).
        entries.removeIf(e -> e.label().length() > 200);
        if (!entries.isEmpty()) return entries;

        // Fall back to quoted-token closed lists ("Bullet", "Sinkable" / "Y" ; "N" / etc.).
        // Only treat as a closed list when at least two distinct short tokens are quoted —
        // single-quoted prose like "Y =  have you completed..." should not qualify.
        Matcher qm = QUOTED_TOKEN.matcher(text);
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        while (qm.find()) {
            String t = qm.group(1).trim();
            if (!t.isEmpty()) tokens.add(t);
        }
        if (tokens.size() >= 2) {
            for (String t : tokens) entries.add(new CodificationDescriptor.ClosedListEntry(t, t));
        }
        return entries;
    }
}
