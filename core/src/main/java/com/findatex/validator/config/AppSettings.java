package com.findatex.validator.config;

import java.util.UUID;

public record AppSettings(External external, Proxy proxy, Feedback feedback,
                          UsageStats usageStats, Newsletter newsletter) {

    /**
     * Normalises missing blocks. Settings files written by older releases have
     * no {@code feedback}/{@code usageStats}/{@code newsletter} key, so Jackson
     * passes {@code null} into the canonical constructor — keep old configs
     * loadable. A blank {@code installId} is regenerated here;
     * {@link SettingsService} detects the generated value and persists it so
     * the id stays stable across runs.
     */
    public AppSettings {
        if (feedback == null) feedback = new Feedback("");
        if (usageStats == null) usageStats = new UsageStats(true, "", "");
        if (newsletter == null) newsletter = new Newsletter("");
    }

    /** Back-compat convenience for the many call sites that predate the feedback block. */
    public AppSettings(External external, Proxy proxy) {
        this(external, proxy, new Feedback(""), null, null);
    }

    /** Back-compat convenience for call sites that predate the usage-stats block. */
    public AppSettings(External external, Proxy proxy, Feedback feedback) {
        this(external, proxy, feedback, null, null);
    }

    /** Back-compat convenience for call sites that predate the newsletter block. */
    public AppSettings(External external, Proxy proxy, Feedback feedback, UsageStats usageStats) {
        this(external, proxy, feedback, usageStats, null);
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

    /**
     * Anonymous usage-statistics opt-out block. {@code enabled} defaults to
     * true. {@code installId} is a random UUID generated once and persisted
     * (no PII / machine binding). {@code endpointUrl} is the web-app ingest
     * URL; blank disables the background sender.
     */
    public record UsageStats(boolean enabled, String installId, String endpointUrl) {
        public UsageStats {
            if (installId == null || installId.isBlank()) {
                installId = UUID.randomUUID().toString();
            }
            if (endpointUrl == null) endpointUrl = "";
        }
    }

    /**
     * Newsletter sign-up configuration. {@code endpointUrl} is the web-app
     * base URL the desktop posts to (it never holds the provider API key —
     * same trust model as {@link UsageStats}). Blank disables the desktop
     * sign-up action.
     */
    public record Newsletter(String endpointUrl) {
        public Newsletter {
            if (endpointUrl == null) endpointUrl = "";
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
                new Feedback(""),
                new UsageStats(true, "", ""),
                new Newsletter(""));
    }

    public AppSettings withExternalEnabled(boolean v) {
        return new AppSettings(
                new External(v, external.lei(), external.isin(), external.cache()),
                proxy, feedback, usageStats, newsletter);
    }

    public AppSettings withFeedbackRepo(String githubRepo) {
        return new AppSettings(external, proxy, new Feedback(githubRepo), usageStats, newsletter);
    }

    public AppSettings withUsageStatsEnabled(boolean v) {
        return new AppSettings(external, proxy, feedback,
                new UsageStats(v, usageStats.installId(), usageStats.endpointUrl()), newsletter);
    }

    public AppSettings withNewsletterEndpoint(String endpointUrl) {
        return new AppSettings(external, proxy, feedback, usageStats, new Newsletter(endpointUrl));
    }
}
