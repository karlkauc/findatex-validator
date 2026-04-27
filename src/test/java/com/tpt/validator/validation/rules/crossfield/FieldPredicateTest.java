package com.tpt.validator.validation.rules.crossfield;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FieldPredicateTest {

    // ---------------------------------------------------------------- NotBlank

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n  "})
    void notBlankRejectsBlanks(String v) {
        assertThat(FieldPredicate.NotBlank.INSTANCE.test(v)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "1", "Cal", "  trimmed  "})
    void notBlankAcceptsNonBlanks(String v) {
        assertThat(FieldPredicate.NotBlank.INSTANCE.test(v)).isTrue();
    }

    @Test
    void notBlankDescribesItself() {
        assertThat(FieldPredicate.NotBlank.INSTANCE.describe()).isEqualTo("is not blank");
    }

    // --------------------------------------------------------------- EqualsAny

    @Test
    void equalsAnySingleValueIsCaseInsensitive() {
        FieldPredicate p = FieldPredicate.EqualsAny.of("1");
        assertThat(p.test("1")).isTrue();
        assertThat(p.test(" 1 ")).isTrue();
        assertThat(p.test("1.0")).isTrue();        // Excel-numeric tail tolerated
        assertThat(p.test("2")).isFalse();
        assertThat(p.test("")).isFalse();
        assertThat(p.test(null)).isFalse();
    }

    @Test
    void equalsAnyMultipleValuesMatchesAny() {
        FieldPredicate p = FieldPredicate.EqualsAny.of("Cal", "Put");
        assertThat(p.test("Cal")).isTrue();
        assertThat(p.test("Put")).isTrue();
        assertThat(p.test("CAL")).isTrue();         // case-insensitive
        assertThat(p.test("put")).isTrue();
        assertThat(p.test("Cap")).isFalse();
        assertThat(p.test("Flr")).isFalse();
    }

    @Test
    void equalsAnyNumericList() {
        FieldPredicate p = FieldPredicate.EqualsAny.of("1", "2", "3");
        assertThat(p.test("1")).isTrue();
        assertThat(p.test("3")).isTrue();
        assertThat(p.test("4")).isFalse();
        assertThat(p.test("9")).isFalse();
    }

    @Test
    void equalsAnyDescribesItself() {
        assertThat(FieldPredicate.EqualsAny.of("1").describe()).isEqualTo("= \"1\"");
        assertThat(FieldPredicate.EqualsAny.of("Cal", "Put").describe())
                .startsWith("∈ ")
                .contains("CAL", "PUT");
    }
}
