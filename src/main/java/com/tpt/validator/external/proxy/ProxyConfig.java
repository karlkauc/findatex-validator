package com.tpt.validator.external.proxy;

import com.tpt.validator.config.AppSettings;

public record ProxyConfig(AppSettings.ProxyMode mode, String host, int port,
                          String user, String password, String nonProxyHosts) {

    public static ProxyConfig from(AppSettings.Proxy p, String decryptedPassword) {
        AppSettings.ManualProxy m = p.manual();
        return new ProxyConfig(p.mode(),
                m.host(), m.port(), m.user(), decryptedPassword, m.nonProxyHosts());
    }
}
