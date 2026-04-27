package com.findatex.validator.validation;

import com.findatex.validator.validation.rules.IsinRule;
import com.findatex.validator.validation.rules.LeiRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IsinLeiRuleTest {

    @Test
    void validIsins() {
        assertThat(IsinRule.isValidIsin("FR0000571085")).isTrue(); // FR Treasury bond
        assertThat(IsinRule.isValidIsin("DE0007164600")).isTrue(); // SAP
        assertThat(IsinRule.isValidIsin("US0378331005")).isTrue(); // Apple
    }

    @Test
    void invalidIsins() {
        assertThat(IsinRule.isValidIsin("FR0000571086")).isFalse(); // wrong check digit
        assertThat(IsinRule.isValidIsin("XX0000000000")).isFalse();
        assertThat(IsinRule.isValidIsin("TOO_SHORT")).isFalse();
        assertThat(IsinRule.isValidIsin(null)).isFalse();
    }

    @Test
    void validLei() {
        // Public LEIs (mod-97 = 1).
        assertThat(LeiRule.isValidLei("529900D6BF99LW9R2E68")).isTrue(); // SAP SE
        assertThat(LeiRule.isValidLei("5493001KJTIIGC8Y1R12")).isTrue(); // BMW AG
    }

    @Test
    void invalidLei() {
        assertThat(LeiRule.isValidLei("529900D6BF99LW9R2E69")).isFalse();
        assertThat(LeiRule.isValidLei("INVALID_LEI_CODE_001")).isFalse();
        assertThat(LeiRule.isValidLei(null)).isFalse();
    }
}
