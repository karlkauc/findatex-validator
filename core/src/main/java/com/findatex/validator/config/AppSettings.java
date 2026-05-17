package com.findatex.validator.config;

public record AppSettings(External external, Proxy proxy, Feedback feedback) {

    /**
     * Normalises a missing {@code feedback} block. Settings files written by
     * pre-feedback releases have no {@code feedback} key, so Jackson passes
     * {@code null} into the canonical constructor — keep old configs loadable.
     */
    public AppSettings {
        if (feedback == null) feedback = new Feedback("");
    }

    /** Back-compat convenience for the many call sites that predate the feedback block. */
    public AppSettings(External external, Proxy proxy) {
        this(external, proxy, new Feedback(""));
    }

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

    /**
     * Feedback ("Report a false positive") configuration. {@code githubRepo} is
     * an {@code owner/repo} slug; empty means the feature is not configured and
     * the UI hides/disables the report action.
     */
    public record Feedback(String githubRepo) {
        public Feedback {
            if (githubRepo == null) githubRepo = "";
        }
    }

    public static AppSettings defaults() {
        return new AppSettings(
                new External(
                        false,
                        new Lei(true, true, false, false),
                        new Isin(true, "", false, false),
                        new Cache(7, "")),
                new Proxy(
                        ProxyMode.SYSTEM,
                        new ManualProxy("", 0, "", "", "localhost|127.0.0.1")),
                new Feedback(""));
    }

    public AppSettings withExternalEnabled(boolean v) {
        return new AppSettings(
                new External(v, external.lei(), external.isin(), external.cache()),
                proxy, feedback);
    }

    public AppSettings withFeedbackRepo(String githubRepo) {
        return new AppSettings(external, proxy, new Feedback(githubRepo));
    }
}
