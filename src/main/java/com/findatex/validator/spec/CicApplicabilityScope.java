package com.findatex.validator.spec;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * TPT-specific applicability scope: a field applies only to certain CIC classes (CIC0..CICF) and,
 * within each class, optionally only to certain sub-categories (e.g. CIC2 → "2" and "9"
 * for convertible/other corporate bonds).
 */
public final class CicApplicabilityScope implements ApplicabilityScope {

    private final Set<String> applicableCic;
    private final Map<String, Set<String>> applicableSubcategories;

    public CicApplicabilityScope(Set<String> applicableCic,
                                 Map<String, Set<String>> applicableSubcategories) {
        this.applicableCic = Collections.unmodifiableSet(new LinkedHashSet<>(applicableCic));
        Map<String, Set<String>> sub = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : applicableSubcategories.entrySet()) {
            sub.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        this.applicableSubcategories = Collections.unmodifiableMap(sub);
    }

    public Set<String> applicableCic() {
        return applicableCic;
    }

    public Map<String, Set<String>> applicableSubcategories() {
        return applicableSubcategories;
    }

    @Override
    public boolean appliesAlways() {
        return applicableCic.isEmpty() || applicableCic.size() == 16;
    }

    /**
     * Tests whether the field applies to a position with the given CIC category digit (3rd char,
     * e.g. {@code "2"}) and sub-category char (4th char, e.g. {@code "1"} for {@code BE21}).
     * Mirrors {@code FieldSpec.appliesToCic(String, String)} from before the scope refactor.
     */
    public boolean appliesToCic(String cicCategoryDigit, String cicSubcategoryChar) {
        if (cicCategoryDigit == null) return appliesAlways();
        if (applicableCic.isEmpty()) return true;
        String cicName = "CIC" + cicCategoryDigit.toUpperCase(Locale.ROOT);
        if (!applicableCic.contains(cicName)) return false;

        Set<String> allowedSubs = applicableSubcategories.get(cicName);
        if (allowedSubs == null || allowedSubs.isEmpty()) return true;
        if (cicSubcategoryChar == null) return true;
        return allowedSubs.contains(cicSubcategoryChar.toUpperCase(Locale.ROOT));
    }
}
