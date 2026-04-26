package com.tpt.validator.spec;

import java.util.EnumMap;
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
        this.numData = numData;
        this.numKey = extractNumKey(numData);
        this.fundXmlPath = fundXmlPath;
        this.definition = definition;
        this.comment = comment;
        this.codificationRaw = codificationRaw;
        this.codification = codification;
        this.flags = new EnumMap<>(flags);
        this.applicableCic = Set.copyOf(applicableCic);
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

    public boolean appliesToAllCic() {
        return applicableCic.isEmpty() || applicableCic.size() == 16;
    }

    public boolean appliesToCic(String cicCategoryDigit) {
        if (cicCategoryDigit == null) return appliesToAllCic();
        if (applicableCic.isEmpty()) return true;
        return applicableCic.contains("CIC" + cicCategoryDigit.toUpperCase());
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
