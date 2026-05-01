package com.findatex.validator.validation.rules;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * If field 15 (Type_of_identification_code_for_the_instrument) = "1" (ISO 6166 / ISIN),
 * then field 14 (Identification_code_of_the_instrument) must be a valid 12-char ISIN with Luhn checksum.
 * Same logic for fields 48 (issuer code type) -> 47 (issuer code), 51 -> 50, etc.
 */
public final class IsinRule implements Rule {

    private final String codeNumKey;
    private final String typeNumKey;

    public IsinRule(String codeNumKey, String typeNumKey) {
        this.codeNumKey = codeNumKey;
        this.typeNumKey = typeNumKey;
    }

    public String codeNumKey() { return codeNumKey; }

    public String typeNumKey() { return typeNumKey; }

    @Override
    public String id() { return "ISIN/" + codeNumKey + "/" + typeNumKey; }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        for (TptRow row : ctx.file().rows()) {
            String type = row.stringValue(typeNumKey).orElse("");
            if (!"1".equals(type.trim())) continue;
            String code = row.stringValue(codeNumKey).orElse("");
            if (code.isBlank()) continue;
            String c = code.trim().toUpperCase(Locale.ROOT);
            if (!isValidIsin(c)) {
                out.add(Finding.error(
                        id(), null, codeNumKey, "ISIN check on field " + codeNumKey,
                        row.rowIndex(), code,
                        "Invalid ISIN: must be 12 chars + Luhn checksum"));
            }
        }
        return out;
    }

    public static boolean isValidIsin(String isin) {
        if (isin == null || isin.length() != 12) return false;
        if (!isin.chars().allMatch(c -> Character.isLetterOrDigit(c))) return false;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            char ch = isin.charAt(i);
            if (Character.isDigit(ch)) digits.append(ch);
            else digits.append(Character.getNumericValue(ch));
        }
        // Luhn from rightmost (excluding check digit), doubling alternating from the right.
        int sum = 0;
        boolean doubleIt = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        int check = (10 - (sum % 10)) % 10;
        return check == Character.getNumericValue(isin.charAt(11));
    }
}
