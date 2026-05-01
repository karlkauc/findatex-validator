package com.findatex.validator.validation.rules;

import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.CodificationDescriptor;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.ValidationContext;
import com.findatex.validator.validation.refdata.IsoData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates the lexical format of a single field according to its codification. */
public final class FormatRule implements Rule {

    /** Strict ISO 8601 date — uses the {@code STRICT} resolver style with a proleptic year
     *  ({@code uuuu}) so impossible dates like 2025-02-30 are rejected instead of being
     *  clamped to the last valid day of the month. */
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    /** NACE V2.1: a single sector letter A..U optionally followed by 1..4 digits (no dots, per spec). */
    private static final Pattern NACE_PATTERN = Pattern.compile("^[A-U]\\d{0,4}$");
    private static final Pattern CIC_PATTERN = Pattern.compile("^[A-Z]{2}[0-9A-F][0-9A-Z]$");
    private static final Set<String> CURRENCY_EXTRA =
            Set.of("CNH", "CNT", "GGP", "IMP", "JEP", "KID", "NIS", "PRB", "TVD");

    private final FieldSpec spec;

    public FormatRule(FieldSpec spec) {
        this.spec = spec;
    }

    public FieldSpec spec() { return spec; }

    @Override
    public String id() { return "FORMAT/" + spec.numKey(); }

    @Override
    public List<Finding> evaluate(ValidationContext ctx) {
        List<Finding> out = new ArrayList<>();
        CodificationDescriptor codif = spec.codification();
        for (TptRow row : ctx.file().rows()) {
            String v = row.stringValue(spec).orElse(null);
            if (v == null) continue;     // presence handled elsewhere
            String error = checkFormat(codif, v);
            if (error != null) {
                out.add(Finding.error(
                        id(), null, spec.numKey(), spec.numData(),
                        row.rowIndex(), v, error));
            }
        }
        return out;
    }

    private String checkFormat(CodificationDescriptor codif, String raw) {
        String v = raw.trim();
        switch (codif.kind()) {
            case NUMERIC: {
                String n = v.replace(",", ".");
                try {
                    Double.parseDouble(n);
                    return null;
                } catch (NumberFormatException e) {
                    return "Expected numeric value (floating decimal)";
                }
            }
            case DATE: {
                try {
                    LocalDate.parse(v, ISO_DATE);
                    return null;
                } catch (DateTimeParseException e) {
                    return "Expected ISO 8601 date YYYY-MM-DD";
                }
            }
            case ISO_4217: {
                String u = v.toUpperCase(Locale.ROOT);
                if (u.length() != 3) return "Expected ISO 4217 currency code (3 letters)";
                if (IsoData.isCurrency(u) || CURRENCY_EXTRA.contains(u)) return null;
                return "Unknown ISO 4217 currency code '" + u + "'";
            }
            case ISO_3166_A2: {
                String u = v.toUpperCase(Locale.ROOT);
                if (u.length() != 2) return "Expected ISO 3166-1 alpha-2 country code (2 letters)";
                if (IsoData.isCountry(u)) return null;
                return "Unknown ISO 3166-1 alpha-2 country code '" + u + "'";
            }
            case NACE: {
                String u = v.toUpperCase(Locale.ROOT);
                if (!NACE_PATTERN.matcher(u).matches()) {
                    return "Invalid NACE code (expected letter A..U optionally followed by digits)";
                }
                return null;
            }
            case CIC: {
                String u = v.toUpperCase(Locale.ROOT);
                if (!CIC_PATTERN.matcher(u).matches()) {
                    return "Invalid CIC code (expected 2 letters + digit/A-F + alphanumeric, length 4)";
                }
                return null;
            }
            case ALPHA: {
                int max = codif.maxLength().orElse(Integer.MAX_VALUE);
                if (v.length() > max) return "Value exceeds Alpha(" + max + ") length";
                if (!v.chars().allMatch(c -> Character.isLetter(c) || c == ' ')) {
                    // Alpha codifications in the spec also allow short codes like "Y","N","Cal".
                    // Be lenient: only flag presence of digits.
                    if (v.chars().anyMatch(Character::isDigit)) {
                        return "Expected alphabetic value, got digits";
                    }
                }
                return null;
            }
            case ALPHANUMERIC: {
                int max = codif.maxLength().orElse(Integer.MAX_VALUE);
                if (v.length() > max) return "Value exceeds Alphanum(max " + max + ") length";
                return null;
            }
            case CLOSED_LIST: {
                if (codif.hasClosedList()
                        && codif.closedList().stream().noneMatch(e -> e.code().equalsIgnoreCase(v))) {
                    return "Value '" + v + "' is not in the closed list ("
                            + codif.closedList().stream()
                                    .map(CodificationDescriptor.ClosedListEntry::code)
                                    .reduce((a, b) -> a + ", " + b).orElse("") + ")";
                }
                return null;
            }
            case FREE_TEXT:
            case UNKNOWN:
            default:
                return null;
        }
    }
}
