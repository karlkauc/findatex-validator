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
 */
public final class LeiRule implements Rule {

    private final String codeNumKey;
    private final String typeNumKey;

    public LeiRule(String codeNumKey, String typeNumKey) {
        this.codeNumKey = codeNumKey;
        this.typeNumKey = typeNumKey;
    }

    @Override
    public String id() { return "LEI/" + codeNumKey + "/" + typeNumKey; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            String type = row.stringValue(typeNumKey).orElse("");
            if (!"1".equals(type.trim())) continue;
            String code = row.stringValue(codeNumKey).orElse("");
            if (code.isBlank()) continue;
            String c = code.trim().toUpperCase(Locale.ROOT);
            if (!isValidLei(c)) {
                out.add(Finding.error(
                        id(), null, codeNumKey, "LEI check on field " + codeNumKey,
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
