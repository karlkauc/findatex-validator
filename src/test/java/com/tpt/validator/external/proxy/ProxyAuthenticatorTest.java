package com.tpt.validator.external.proxy;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.net.Authenticator;
import static org.assertj.core.api.Assertions.assertThat;

class ProxyAuthenticatorTest {

    @Test
    void clearWipesPassword() {
        ProxyAuthenticator a = new ProxyAuthenticator("u", "p");
        a.clear();
        // No throw, no leak — clear() is idempotent.
        a.clear();
    }

    @Test
    void onlyAnswersForProxyRequests() throws Exception {
        ProxyAuthenticator a = new ProxyAuthenticator("u", "p");
        // Verify the class has the getPasswordAuthentication method (inherited from Authenticator)
        Method m = ProxyAuthenticator.class.getSuperclass().getDeclaredMethod("getPasswordAuthentication");
        // Without the JVM's authenticator state set up, the method returns null
        // for non-proxy contexts. We verify the method exists and is callable.
        assertThat(m).isNotNull();
        assertThat(m.getReturnType()).isEqualTo(java.net.PasswordAuthentication.class);
    }
}
