package com.tpt.validator.config;

public record AppSettings(External external, Proxy proxy) {

    public enum ProxyMode { SYSTEM, MANUAL, NONE }

    public record External(boolean enabled, Lei lei, Isin isin, Cache cache) {}
    public record Lei(boolean enabled, boolean checkLapsedStatus,
                      boolean checkIssuerName, boolean checkIssuerCountry) {}
    public record Isin(boolean enabled, String openFigiApiKey,
                       boolean checkCurrency, boolean checkCicConsistency) {}
    public record Cache(int ttlDays, String directory) {}
    public record Proxy(ProxyMode mode, ManualProxy manual) {}
    public record ManualProxy(String host, int port, String user,
                              String passwordEncrypted, String nonProxyHosts) {}

    public static AppSettings defaults() {
        return new AppSettings(
                new External(
                        false,
                        new Lei(true, true, false, false),
                        new Isin(true, "", false, false),
                        new Cache(7, "")),
                new Proxy(
                        ProxyMode.SYSTEM,
                        new ManualProxy("", 0, "", "", "localhost|127.0.0.1")));
    }

    public AppSettings withExternalEnabled(boolean v) {
        return new AppSettings(
                new External(v, external.lei(), external.isin(), external.cache()),
                proxy);
    }
}
