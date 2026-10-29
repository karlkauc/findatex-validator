package com.findatex.validator.validation.rules;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.ValidationContext;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where the field type indicates "1 - LEI" (e.g. fields 82, 85, 99, 102, 141 carry "1" or "9"),
 * the corresponding code field must be a 20-char LEI matching ISO 17442 (mod-97 check).
 *
 * <p>A rule built with the single-argument constructor has no type column: the spec fixes the
 * column to LEI outright (TPT V8 fields 115, 119, 140, whose former type columns are N/A), so
 * every non-blank value is checked.
 */
public final class LeiRule implements Rule {

    private final String codeNumKey;
    private final String typeNumKey;

    public LeiRule(String codeNumKey, String typeNumKey) {
        this.codeNumKey = codeNumKey;
        this.typeNumKey = typeNumKey == null ? "" : typeNumKey;
    }

    /** Single-purpose LEI column: no sibling type flag, every non-blank value must be an LEI. */
    public LeiRule(String codeNumKey) {
        this(codeNumKey, "");
    }

    public String codeNumKey() { return codeNumKey; }

    /** Sibling type column, or {@code ""} when the column is single-purpose. */
    public String typeNumKey() { return typeNumKey; }

    public boolean hasTypeFlag() { return !typeNumKey.isEmpty(); }

    @Override
    public String id() { return hasTypeFlag() ? "LEI/" + codeNumKey + "/" + typeNumKey : "LEI/" + codeNumKey; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        String fieldName = ctx.catalog().byNumKey(codeNumKey)
                .map(com.findatex.validator.spec.FieldSpec::name)
                .orElse("LEI check on field " + codeNumKey);
        for (TptRow row : ctx.file().rows()) {
            if (hasTypeFlag()) {
                String type = row.stringValue(typeNumKey).orElse("");
                if (!"1".equals(type.trim())) continue;
            }
            String code = row.stringValue(codeNumKey).orElse("");
            if (code.isBlank()) continue;
            String c = code.trim().toUpperCase(Locale.ROOT);
            if (!isValidLei(c)) {
                out.add(Finding.error(
                        id(), null, codeNumKey, fieldName,
                        row.rowIndex(), code,
                        "Invalid LEI: must be 20 alphanumeric chars with ISO 17442 mod-97 checksum"));
            }
        }
        return out;
    }

    public static boolean isValidLei(String lei) {
        if (lei == null || lei.length() != 20) return false;
        if (!lei.chars().allMatch(Character::isLetterOrDigit)) return false;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < lei.length(); i++) {
            char ch = lei.charAt(i);
            if (Character.isDigit(ch)) digits.append(ch);
            else digits.append(Character.getNumericValue(ch));
        }
        try {
            BigInteger n = new BigInteger(digits.toString());
            return n.mod(BigInteger.valueOf(97)).intValue() == 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
