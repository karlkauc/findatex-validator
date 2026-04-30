package com.findatex.validator.validation.rules.crossfield;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Boolean test on a TPT cell value, used by {@link ConditionalRequirement} to
 * decide whether a cross-field conditional applies on a given row.
 *
 * <ul>
 *   <li>{@link EqualsAny} — case-insensitive membership against a fixed set of
 *       values, e.g. {@code "1"}, {@code "Cal"}, {@code "Put"}.</li>
 *   <li>{@link NotBlank} — value is present and non-whitespace.</li>
 *   <li>{@link GreaterThan} — value parses as a number strictly above a
 *       threshold (used by EET PAI-Coverage gating and EET 615/616 country
 *       lists, where the spec says e.g. "Conditional to 31210 &gt; 0").</li>
 * </ul>
 */
public sealed interface FieldPredicate
        permits FieldPredicate.EqualsAny, FieldPredicate.NotBlank, FieldPredicate.GreaterThan {

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

    record GreaterThan(double threshold) implements FieldPredicate {

        public static GreaterThan of(double threshold) {
            return new GreaterThan(threshold);
        }

        @Override
        public boolean test(String value) {
            if (value == null) return false;
            String norm = value.trim().replace(',', '.');
            if (norm.isEmpty()) return false;
            try {
                return Double.parseDouble(norm) > threshold;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        public String describe() {
            // Render integer-valued thresholds without a trailing ".0".
            String t = (threshold == Math.floor(threshold) && !Double.isInfinite(threshold))
                    ? Long.toString((long) threshold)
                    : Double.toString(threshold);
            return "> " + t;
        }
    }
}
