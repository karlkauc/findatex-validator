package com.findatex.validator.validation.rules.crossfield;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Boolean test on a TPT cell value, used by {@link ConditionalRequirement} to
 * decide whether a cross-field conditional applies on a given row. Two simple
 * implementations cover every pattern the TPT V7 spec uses:
 *
 * <ul>
 *   <li>{@link EqualsAny} — case-insensitive membership against a fixed set of
 *       values, e.g. {@code "1"}, {@code "Cal"}, {@code "Put"}.</li>
 *   <li>{@link NotBlank} — value is present and non-whitespace.</li>
 * </ul>
 */
public sealed interface FieldPredicate permits FieldPredicate.EqualsAny, FieldPredicate.NotBlank {

    boolean test(String value);

    /** Short, human-readable form of the predicate, used in finding messages. */
    String describe();

    record EqualsAny(Set<String> allowedUpper) implements FieldPredicate {

        public EqualsAny(Set<String> allowedUpper) {
            this.allowedUpper = Collections.unmodifiableSet(
                    new LinkedHashSet<>(allowedUpper.stream()
                            .map(s -> s == null ? "" : s.trim().toUpperCase(Locale.ROOT))
                            .toList()));
        }

        public static EqualsAny of(String... values) {
            return new EqualsAny(new LinkedHashSet<>(Arrays.asList(values)));
        }

        @Override
        public boolean test(String value) {
            if (value == null) return false;
            String norm = value.trim().toUpperCase(Locale.ROOT);
            // Tolerate trailing ".0" coming from Excel numeric cells.
            if (norm.endsWith(".0")) norm = norm.substring(0, norm.length() - 2);
            return allowedUpper.contains(norm);
        }

        @Override
        public String describe() {
            if (allowedUpper.size() == 1) return "= \"" + allowedUpper.iterator().next() + "\"";
            return "∈ " + allowedUpper;
        }
    }

    record NotBlank() implements FieldPredicate {

        public static final NotBlank INSTANCE = new NotBlank();

        @Override
        public boolean test(String value) {
            return value != null && !value.trim().isEmpty();
        }

        @Override
        public String describe() {
            return "is not blank";
        }
    }
}
