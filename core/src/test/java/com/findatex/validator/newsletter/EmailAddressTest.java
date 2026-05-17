package com.findatex.validator.newsletter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAddressTest {

    @Test
    void acceptsPlausibleAddresses() {
        assertThat(EmailAddress.isValid("a@b.co")).isTrue();
        assertThat(EmailAddress.isValid("  name.surname+tag@sub.example.org ")).isTrue();
    }

    @Test
    void rejectsObviousJunk() {
        assertThat(EmailAddress.isValid(null)).isFalse();
        assertThat(EmailAddress.isValid("")).isFalse();
        assertThat(EmailAddress.isValid("no-at-sign")).isFalse();
        assertThat(EmailAddress.isValid("two@@example.com")).isFalse();
        assertThat(EmailAddress.isValid("no@domain")).isFalse();
        assertThat(EmailAddress.isValid("spaces in@example.com")).isFalse();
        assertThat(EmailAddress.isValid("a@" + "x".repeat(300) + ".com")).isFalse();
    }

    @Test
    void normaliseTrimsAndNullSafe() {
        assertThat(EmailAddress.normalise("  x@y.z  ")).isEqualTo("x@y.z");
        assertThat(EmailAddress.normalise(null)).isEmpty();
    }
}
