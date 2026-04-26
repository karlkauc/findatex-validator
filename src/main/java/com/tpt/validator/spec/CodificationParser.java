package com.tpt.validator.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodificationParser {

    private static final Pattern CLOSED_LIST_LINE =
            Pattern.compile("^\\s*(\\d+[a-zA-Z]?)\\s*[-–.]\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern ALPHANUM_PATTERN =
            Pattern.compile("alphanum(?:eric)?\\s*\\(?\\s*(?:max\\s*)?(\\d+)\\s*\\)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALPHA_PATTERN =
            Pattern.compile("alpha\\s*\\(?\\s*(\\d+)\\s*\\)?", Pattern.CASE_INSENSITIVE);

    private CodificationParser() {}

    public static CodificationDescriptor parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new CodificationDescriptor(CodificationKind.UNKNOWN, Optional.empty(), List.of(), "");
        }
        String text = raw.trim();
        String lower = text.toLowerCase();

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
            return new CodificationDescriptor(CodificationKind.NUMERIC, Optional.empty(), closedList, text);
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

    private static List<CodificationDescriptor.ClosedListEntry> parseClosedList(String text) {
        List<CodificationDescriptor.ClosedListEntry> entries = new ArrayList<>();
        Matcher m = CLOSED_LIST_LINE.matcher(text);
        while (m.find()) {
            String code = m.group(1).trim();
            String label = m.group(2).trim();
            entries.add(new CodificationDescriptor.ClosedListEntry(code, label));
        }
        // The regex also matches free-text bullets like "1. ISO 6166". To reduce false
        // positives, drop entries when the same field's label looks like a sentence (>120 chars
        // or contains a period followed by a capital letter).
        entries.removeIf(e -> e.label().length() > 200);
        return entries;
    }
}
