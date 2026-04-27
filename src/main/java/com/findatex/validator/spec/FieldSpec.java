package com.findatex.validator.spec;

import com.findatex.validator.template.api.ProfileKey;

import java.util.Collections;
import java.util.LinkedHashMap;
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
    /** Storage is keyed by profile code (e.g. {@code "SOLVENCY_II"}) so non-TPT templates can reuse this class. */
    private final Map<String, Flag> flags;
    private final ApplicabilityScope applicabilityScope;
    private final int sourceRow;

    /** Convenience constructor accepting profile-key-keyed flags and a CIC set, no sub-categories. */
    public FieldSpec(String numData,
                     String fundXmlPath,
                     String definition,
                     String comment,
                     String codificationRaw,
                     CodificationDescriptor codification,
                     Map<ProfileKey, Flag> profileFlags,
                     Set<String> applicableCic,
                     int sourceRow) {
        this(numData, fundXmlPath, definition, comment, codificationRaw, codification,
                profileFlags, applicableCic, Map.of(), sourceRow);
    }

    /** Convenience constructor accepting profile-key-keyed flags, a CIC set and sub-categories. */
    public FieldSpec(String numData,
                     String fundXmlPath,
                     String definition,
                     String comment,
                     String codificationRaw,
                     CodificationDescriptor codification,
                     Map<ProfileKey, Flag> profileFlags,
                     Set<String> applicableCic,
                     Map<String, Set<String>> applicableSubcategories,
                     int sourceRow) {
        this(numData, fundXmlPath, definition, comment, codificationRaw, codification,
                profileKeyedToCodes(profileFlags),
                (applicableCic == null || applicableCic.isEmpty())
                        && (applicableSubcategories == null || applicableSubcategories.isEmpty())
                        ? EmptyApplicabilityScope.INSTANCE
                        : new CicApplicabilityScope(applicableCic, applicableSubcategories),
                sourceRow);
    }

    private static Map<String, Flag> profileKeyedToCodes(Map<ProfileKey, Flag> in) {
        Map<String, Flag> out = new LinkedHashMap<>();
        for (Map.Entry<ProfileKey, Flag> e : in.entrySet()) {
            out.put(e.getKey().code(), e.getValue());
        }
        return out;
    }

    /** Direct constructor for templates that supply a scope explicitly (post-Phase-0 callers). */
    public FieldSpec(String numData,
                     String fundXmlPath,
                     String definition,
                     String comment,
                     String codificationRaw,
                     CodificationDescriptor codification,
                     Map<String, Flag> codeKeyedFlags,
                     ApplicabilityScope applicabilityScope,
                     int sourceRow) {
        this.numData = numData;
        this.numKey = extractNumKey(numData);
        this.fundXmlPath = fundXmlPath;
        this.definition = definition;
        this.comment = comment;
        this.codificationRaw = codificationRaw;
        this.codification = codification;
        this.flags = Collections.unmodifiableMap(new LinkedHashMap<>(codeKeyedFlags));
        this.applicabilityScope = applicabilityScope == null ? EmptyApplicabilityScope.INSTANCE : applicabilityScope;
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

    /** Look up a flag by profile code (e.g. {@code "SOLVENCY_II"}). Used by template-agnostic code paths. */
    public Flag flag(String profileCode) {
        return flags.getOrDefault(profileCode, Flag.UNKNOWN);
    }

    /** Look up a flag by {@link ProfileKey} — the post-Phase-0 way to address profiles. */
    public Flag flag(ProfileKey p) {
        return flags.getOrDefault(p.code(), Flag.UNKNOWN);
    }

    /** Read-only view of the flag map keyed by profile code. */
    public Map<String, Flag> flagsByCode() {
        return flags;
    }

    public ApplicabilityScope applicabilityScope() { return applicabilityScope; }

    public Set<String> applicableCic() {
        return applicabilityScope instanceof CicApplicabilityScope cic ? cic.applicableCic() : Set.of();
    }

    /**
     * Sub-category restrictions per CIC class, e.g. {@code CIC2 -> {"2", "9"}} when the
     * spec qualifier reads {@code "x for convertible bonds \"22\" or other corporate bonds \"29\""}.
     * A CIC class missing from this map (or mapped to an empty set) means "no sub-category
     * restriction" — every sub-category in that class applies.
     */
    public Map<String, Set<String>> applicableSubcategories() {
        return applicabilityScope instanceof CicApplicabilityScope cic
                ? cic.applicableSubcategories()
                : Map.of();
    }

    public boolean appliesToAllCic() {
        return applicabilityScope.appliesAlways();
    }

    /** Backward-compatible category-only check. Use {@link #appliesToCic(String, String)} when the sub-category is known. */
    public boolean appliesToCic(String cicCategoryDigit) {
        return appliesToCic(cicCategoryDigit, null);
    }

    /**
     * Tests whether the field applies to a position with the given CIC category digit (3rd char,
     * e.g. {@code "2"}) and sub-category char (4th char, e.g. {@code "1"} for {@code BE21}).
     * Delegates to {@link CicApplicabilityScope}; templates without a CIC dimension always return true.
     */
    public boolean appliesToCic(String cicCategoryDigit, String cicSubcategoryChar) {
        if (applicabilityScope instanceof CicApplicabilityScope cic) {
            return cic.appliesToCic(cicCategoryDigit, cicSubcategoryChar);
        }
        return true;
    }

    public boolean isMandatoryFor(ProfileKey p) {
        return flag(p) == Flag.M;
    }

    public boolean isConditionalFor(ProfileKey p) {
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
