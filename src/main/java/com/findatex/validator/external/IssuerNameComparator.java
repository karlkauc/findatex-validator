package com.findatex.validator.external;

import java.text.Normalizer;
import java.util.Set;

public final class IssuerNameComparator {

    private static final Set<String> SUFFIXES = Set.of(
            "inc", "incorporated", "corp", "corporation",
            "ltd", "limited",
            "sa", "ag", "se", "nv", "bv", "plc",
            "gmbh", "kgaa", "spa", "sas", "ab",
            "co", "company", "lp", "llc", "llp"
    );

    private IssuerNameComparator() {}

    /** True if either side is empty (treat as "no comparison possible") or normalisations match. */
    public static boolean equivalent(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) return true;
        return normalise(a).equals(normalise(b));
    }

    static String normalise(String in) {
        String n = Normalizer.normalize(in, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        StringBuilder out = new StringBuilder();
        for (String token : n.split(" ")) {
            if (!SUFFIXES.contains(token)) {
                if (out.length() > 0) out.append(' ');
                out.append(token);
            }
        }
        return out.toString();
    }
}
