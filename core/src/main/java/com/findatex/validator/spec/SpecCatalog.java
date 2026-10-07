package com.findatex.validator.spec;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class SpecCatalog {

    private final List<FieldSpec> fields;
    private final Map<String, FieldSpec> byNumKey;
    private final Map<String, FieldSpec> byNumData;
    private final Map<String, FieldSpec> byName;
    private final Map<String, FieldSpec> byPath;
    /** Spelling-tolerant keys (see {@link #loose}); ambiguous keys are left out. */
    private final Map<String, FieldSpec> byLooseName;
    private final Map<String, FieldSpec> byLooseLabel;

    /** The leading field number of a column name, e.g. {@code 12_}, {@code 8b_}, {@code 00010_}. */
    private static final Pattern NUMBER_PREFIX = Pattern.compile("^\\s*\\d+[A-Za-z]?[_\\s.:\\-]+");

    public SpecCatalog(List<FieldSpec> fields) {
        this.fields = List.copyOf(fields);
        this.byNumKey = new LinkedHashMap<>();
        this.byNumData = new LinkedHashMap<>();
        this.byName = new LinkedHashMap<>();
        this.byPath = new LinkedHashMap<>();
        this.byLooseName = new LinkedHashMap<>();
        this.byLooseLabel = new LinkedHashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (FieldSpec f : fields) {
            byNumKey.putIfAbsent(f.numKey(), f);
            byNumData.putIfAbsent(normalize(f.numData()), f);
            if (f.name() != null && !f.name().isBlank()) {
                byName.putIfAbsent(normalize(f.name()), f);
            }
            if (f.fundXmlPath() != null && !f.fundXmlPath().isBlank()) {
                byPath.putIfAbsent(normalizePath(f.fundXmlPath()), f);
            }
            for (String full : new String[] {f.numData(), f.name()}) {
                if (full == null || !NUMBER_PREFIX.matcher(full).find()) continue;
                putUnambiguous(byLooseName, ambiguous, loose(full), f);
                putUnambiguous(byLooseLabel, ambiguous, loose(NUMBER_PREFIX.matcher(full).replaceFirst("")), f);
            }
        }
        byLooseName.keySet().removeAll(ambiguous);
        byLooseLabel.keySet().removeAll(ambiguous);
    }

    private static void putUnambiguous(Map<String, FieldSpec> index, Set<String> ambiguous,
                                       String key, FieldSpec f) {
        if (key.isEmpty()) return;
        FieldSpec previous = index.putIfAbsent(key, f);
        if (previous != null && previous != f) ambiguous.add(key);
    }

    public List<FieldSpec> fields() { return fields; }

    public Optional<FieldSpec> byNumKey(String key) {
        return Optional.ofNullable(byNumKey.get(key));
    }

    public Optional<FieldSpec> byNumData(String numData) {
        return Optional.ofNullable(byNumData.get(normalize(numData)));
    }

    public Optional<FieldSpec> byName(String name) {
        return Optional.ofNullable(byName.get(normalize(name)));
    }

    public Optional<FieldSpec> byPath(String path) {
        return Optional.ofNullable(byPath.get(normalizePath(path)));
    }

    /**
     * Header name match: try numKey, numData, display name, FundsXML path, and finally the
     * full column name with its spelling relaxed — {@code 1. Portfolio identifying data},
     * {@code 1 - Portfolio_identifying_data}, {@code 0001_Portfolio_identifying_data} or the
     * label without its number all resolve to field 1, as long as the label is unique in
     * the template.
     */
    public Optional<FieldSpec> matchHeader(String header) {
        if (header == null || header.isBlank()) return Optional.empty();
        String h = header.trim();

        // Pure num token? (e.g. "12", "8b")
        Optional<FieldSpec> n = byNumKey(h);
        if (n.isPresent()) return n;

        // Numbered prefix like "12_..." → take the numKey.
        int us = h.indexOf('_');
        if (us > 0) {
            Optional<FieldSpec> p = byNumKey(h.substring(0, us));
            if (p.isPresent()) return p;
        }

        Optional<FieldSpec> nd = byNumData(h);
        if (nd.isPresent()) return nd;

        Optional<FieldSpec> bn = byName(h);
        if (bn.isPresent()) return bn;

        Optional<FieldSpec> bp = byPath(h);
        if (bp.isPresent()) return bp;

        String l = loose(h);
        FieldSpec ln = byLooseName.get(l);
        if (ln != null) return Optional.of(ln);
        // A bare number is never a label: "7" must not resolve through a field called "..._7".
        if (l.chars().anyMatch(Character::isLetter)) return Optional.ofNullable(byLooseLabel.get(l));
        return Optional.empty();
    }

    /** Lower-case letters and digits only, leading zeros dropped. */
    private static String loose(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) sb.append(c);
        }
        int start = 0;
        while (start < sb.length() - 1 && sb.charAt(start) == '0') start++;
        return sb.substring(start);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replace(" ", " ");
    }

    private static String normalizePath(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "").toLowerCase();
    }
}
