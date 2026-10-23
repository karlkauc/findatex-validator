package com.findatex.validator.external.proxy;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import static org.assertj.core.api.Assertions.assertThat;

class ProxyAuthenticatorTest {

    @Test
    void clearIsIdempotent() {
        ProxyAuthenticator a = new ProxyAuthenticator("proxy.corp", 8080, "u", "p");
        a.clear();
        a.clear(); // second clear must not throw
    }

    @Test
    void protectedMethodIsInherited() throws Exception {
        ProxyAuthenticator a = new ProxyAuthenticator("proxy.corp", 8080, "u", "p");
        Method m = a.getClass().getSuperclass().getDeclaredMethod("getPasswordAuthentication");
        // Method exists and returns PasswordAuthentication
        assertThat(m).isNotNull();
        assertThat(m.getReturnType()).isEqualTo(java.net.PasswordAuthentication.class);
    }

    @Test
    void answersOnlyTheConfiguredProxy() {
        ProxyAuthenticator a = new ProxyAuthenticator("Proxy.Corp", 8080, "u", "p");
        assertThat(ask(a, "proxy.corp", 8080, Authenticator.RequestorType.PROXY)).isNotNull()
                .extracting(PasswordAuthentication::getUserName).isEqualTo("u");
        assertThat(ask(a, "wpad-proxy.lan", 8080, Authenticator.RequestorType.PROXY)).isNull();
        assertThat(ask(a, "proxy.corp", 3128, Authenticator.RequestorType.PROXY)).isNull();
        assertThat(ask(a, "proxy.corp", 8080, Authenticator.RequestorType.SERVER)).isNull();
    }

    private static PasswordAuthentication ask(ProxyAuthenticator a, String host, int port,
                                              Authenticator.RequestorType type) {
        return a.requestPasswordAuthenticationInstance(host, null, port, "http", "realm", "Basic",
                null, type);
    }
}
