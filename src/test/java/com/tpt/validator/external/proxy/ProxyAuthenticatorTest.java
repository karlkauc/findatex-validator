package com.tpt.validator.external.proxy;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.assertj.core.api.Assertions.assertThat;

class ProxyAuthenticatorTest {

    @Test
    void clearIsIdempotent() {
        ProxyAuthenticator a = new ProxyAuthenticator("u", "p");
        a.clear();
        a.clear(); // second clear must not throw
    }

    @Test
    void protectedMethodIsInherited() throws Exception {
        ProxyAuthenticator a = new ProxyAuthenticator("u", "p");
        Method m = a.getClass().getSuperclass().getDeclaredMethod("getPasswordAuthentication");
        // Method exists and returns PasswordAuthentication
        assertThat(m).isNotNull();
        assertThat(m.getReturnType()).isEqualTo(java.net.PasswordAuthentication.class);
    }
}
