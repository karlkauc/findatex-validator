package com.tpt.validator.spec;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FieldSpec {

    private final String numData;
    private final String numKey;
    private final String fundXmlPath;
    private final String definition;
    private final String comment;
    private final String codificationRaw;
    private final CodificationDescriptor codification;
    private final Map<Profile, Flag> flags;
    private final Set<String> applicableCic;
    private final Map<String, Set<String>> applicableSubcategories;
    private final int sourceRow;

    public FieldSpec(String numData,
                     String fundXmlPath,
                     String definition,
                     String comment,
                     String codificationRaw,
                     CodificationDescriptor codification,
                     Map<Profile, Flag> flags,
                     Set<String> applicableCic,
                     int sourceRow) {
        this(numData, fundXmlPath, definition, comment, codificationRaw, codification,
                flags, applicableCic, Map.of(), sourceRow);
    }

    public FieldSpec(String numData,
                     String fundXmlPath,
                     String definition,
                     String comment,
                     String codificationRaw,
                     CodificationDescriptor codification,
                     Map<Profile, Flag> flags,
                     Set<String> applicableCic,
                     Map<String, Set<String>> applicableSubcategories,
                     int sourceRow) {
        this.numData = numData;
        this.numKey = extractNumKey(numData);
        this.fundXmlPath = fundXmlPath;
        this.definition = definition;
        this.comment = comment;
        this.codificationRaw = codificationRaw;
        this.codification = codification;
        this.flags = new EnumMap<>(flags);
        this.applicableCic = Set.copyOf(applicableCic);
        Map<String, Set<String>> sub = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : applicableSubcategories.entrySet()) {
            sub.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        this.applicableSubcategories = Collections.unmodifiableMap(sub);
        this.sourceRow = sourceRow;
    }

    public String numData() { return numData; }
    public String numKey()  { return numKey; }
    public String fundXmlPath() { return fundXmlPath; }
    public String definition() { return definition; }
    public String comment() { return comment; }
    public String codificationRaw() { return codificationRaw; }
    public CodificationDescriptor codification() { return codification; }
    public int sourceRow() { return sourceRow; }

    public Flag flag(Profile p) {
        return flags.getOrDefault(p, Flag.UNKNOWN);
    }

    public Set<String> applicableCic() { return applicableCic; }

    /**
     * Sub-category restrictions per CIC class, e.g. {@code CIC2 -> {"2", "9"}} when the
     * spec qualifier reads {@code "x for convertible bonds \"22\" or other corporate bonds \"29\""}.
     * A CIC class missing from this map (or mapped to an empty set) means "no sub-category
     * restriction" — every sub-category in that class applies.
     */
    public Map<String, Set<String>> applicableSubcategories() {
        return applicableSubcategories;
    }

    public boolean appliesToAllCic() {
        return applicableCic.isEmpty() || applicableCic.size() == 16;
    }

    /** Backward-compatible category-only check. Use {@link #appliesToCic(String, String)} when the sub-category is known. */
    public boolean appliesToCic(String cicCategoryDigit) {
        return appliesToCic(cicCategoryDigit, null);
    }

    /**
     * Tests whether the field applies to a position with the given CIC category digit (3rd char,
     * e.g. {@code "2"}) and sub-category char (4th char, e.g. {@code "1"} for {@code BE21}).
     *
     * <p>Logic:
     * <ol>
     *   <li>If the field has no CIC restrictions, applies always.</li>
     *   <li>Otherwise the CIC class (e.g. {@code CIC2}) must be listed.</li>
     *   <li>If the CIC class carries a sub-category whitelist, the sub-category char must be in it.</li>
     *   <li>If no sub-category is supplied (null) but a whitelist exists, fall back to applicable=true
     *       so we err on the side of reporting (the missing CIC means the row's CIC didn't parse).</li>
     * </ol>
     */
    public boolean appliesToCic(String cicCategoryDigit, String cicSubcategoryChar) {
        if (cicCategoryDigit == null) return appliesToAllCic();
        if (applicableCic.isEmpty()) return true;
        String cicName = "CIC" + cicCategoryDigit.toUpperCase(Locale.ROOT);
        if (!applicableCic.contains(cicName)) return false;

        Set<String> allowedSubs = applicableSubcategories.get(cicName);
        if (allowedSubs == null || allowedSubs.isEmpty()) return true;       // no restriction
        if (cicSubcategoryChar == null) return true;                         // unknown — be lenient
        return allowedSubs.contains(cicSubcategoryChar.toUpperCase(Locale.ROOT));
    }

    public boolean isMandatoryFor(Profile p) {
        return flag(p) == Flag.M;
    }

    public boolean isConditionalFor(Profile p) {
        return flag(p) == Flag.C;
    }

    /** Extract the leading numeric/alphanumeric NUM token (e.g. "12", "8b", "94b") from the NUM_DATA label. */
    public static String extractNumKey(String numData) {
        if (numData == null) return "";
        String trimmed = numData.trim();
        int us = trimmed.indexOf('_');
        return us < 0 ? trimmed : trimmed.substring(0, us);
    }

}
