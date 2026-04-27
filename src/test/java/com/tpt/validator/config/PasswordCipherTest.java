package com.tpt.validator.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordCipherTest {

    @Test
    void roundTrip() {
        String enc = PasswordCipher.encrypt("hunter2");
        assertThat(enc).isNotEmpty().isNotEqualTo("hunter2");
        assertThat(PasswordCipher.decrypt(enc)).isEqualTo("hunter2");
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        assertThat(PasswordCipher.encrypt("")).isEmpty();
        assertThat(PasswordCipher.decrypt("")).isEmpty();
    }

    @Test
    void tamperedCiphertextDecryptsToEmpty() {
        String enc = PasswordCipher.encrypt("secret");
        String tampered = enc.substring(0, enc.length() - 4) + "ZZZZ";
        assertThat(PasswordCipher.decrypt(tampered)).isEmpty();
    }
}
