package com.findatex.validator.web.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Application-wide config bean for the web layer. All values are overridable via
 * {@code application.properties} or {@code FINDATEX_WEB_*} environment variables.
 */
@ApplicationScoped
public class WebConfig {

    @ConfigProperty(name = "findatex.web.rate-limit.per-ip-per-hour", defaultValue = "10")
    int rateLimitPerIpPerHour;

    @ConfigProperty(name = "findatex.web.max-concurrency", defaultValue = "4")
    int maxConcurrency;

    @ConfigProperty(name = "findatex.web.acquire-timeout-millis", defaultValue = "2000")
    long acquireTimeoutMillis;

    @ConfigProperty(name = "findatex.web.report.ttl-minutes", defaultValue = "5")
    int reportTtlMinutes;

    @ConfigProperty(name = "findatex.web.external.enabled", defaultValue = "false")
    boolean externalEnabled;

    @ConfigProperty(name = "findatex.web.external.openfigi-key")
    Optional<String> externalOpenfigiKey;

    @ConfigProperty(name = "findatex.web.external.proxy-mode", defaultValue = "NONE")
    String externalProxyMode;

    @ConfigProperty(name = "findatex.web.external.proxy-host")
    Optional<String> externalProxyHost;

    @ConfigProperty(name = "findatex.web.external.proxy-port", defaultValue = "0")
    int externalProxyPort;

    @ConfigProperty(name = "findatex.web.external.proxy-username")
    Optional<String> externalProxyUsername;

    @ConfigProperty(name = "findatex.web.external.proxy-password")
    Optional<String> externalProxyPassword;

    @ConfigProperty(name = "findatex.web.external.proxy-non-proxy-hosts")
    Optional<String> externalProxyNonProxyHosts;

    @ConfigProperty(name = "findatex.web.external.cache-dir",
            defaultValue = "${java.io.tmpdir}/findatex-cache")
    String externalCacheDir;

    @ConfigProperty(name = "findatex.web.external.cache-ttl-days", defaultValue = "7")
    int externalCacheTtlDays;

    @ConfigProperty(name = "findatex.web.desktop-download-url")
    Optional<String> desktopDownloadUrl;

    @ConfigProperty(name = "findatex.web.feedback.github-repo")
    Optional<String> feedbackGithubRepo;

    @ConfigProperty(name = "findatex.web.usage-stats.ingest-token")
    Optional<String> usageStatsIngestToken;

    @ConfigProperty(name = "findatex.web.usage-stats.rate-per-ip-per-hour", defaultValue = "60")
    int usageStatsRatePerIpPerHour;

    @ConfigProperty(name = "findatex.web.usage-stats.geoip-db")
    Optional<String> usageStatsGeoipDb;

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    Optional<String> usageDbUrl;

    @ConfigProperty(name = "findatex.web.newsletter.provider", defaultValue = "mailerlite")
    String newsletterProvider;

    @ConfigProperty(name = "findatex.web.newsletter.api-key")
    Optional<String> newsletterApiKey;

    @ConfigProperty(name = "findatex.web.newsletter.group-id")
    Optional<String> newsletterGroupId;

    @ConfigProperty(name = "findatex.web.newsletter.rate-per-ip-per-hour", defaultValue = "5")
    int newsletterRatePerIpPerHour;

    public RateLimit rateLimit() {
        return new RateLimit(rateLimitPerIpPerHour);
    }

    public int maxConcurrency() {
        return maxConcurrency;
    }

    public long acquireTimeoutMillis() {
        return acquireTimeoutMillis;
    }

    public Report report() {
        return new Report(reportTtlMinutes);
    }

    /**
     * Optional download URL for the JavaFX desktop build, surfaced when the web
     * quota is exhausted so users can switch to the unmetered offline tool.
     * Returns empty when unset or set to blank.
     */
    public Optional<String> desktopDownloadUrl() {
        return desktopDownloadUrl.map(String::trim).filter(s -> !s.isEmpty());
    }

    /**
     * {@code owner/repo} slug that "report a false positive" opens a pre-filled
     * GitHub issue against. Empty when unset — the UI then hides the action.
     */
    public Optional<String> feedbackGithubRepo() {
        return feedbackGithubRepo.map(String::trim).filter(s -> !s.isEmpty());
    }

    /**
     * Anonymous usage-stats config. {@code ingestToken} empty => endpoint
     * accepts-and-discards (feature off). {@code dbConfigured} reflects whether
     * a JDBC URL is present at all (drives the inert-when-unconfigured path).
     */
    public UsageStats usageStats() {
        return new UsageStats(
                usageStatsIngestToken.map(String::trim).filter(s -> !s.isEmpty()),
                Math.max(1, usageStatsRatePerIpPerHour),
                usageStatsGeoipDb.map(String::trim).filter(s -> !s.isEmpty()),
                usageDbUrl.map(String::trim).filter(s -> !s.isEmpty()).isPresent());
    }

    /**
     * Newsletter sign-up config. {@code apiKey} empty ⇒ feature inert (endpoint
     * 503, SPA hides the form). The address is forwarded to the provider and
     * never persisted here.
     */
    public Newsletter newsletter() {
        return new Newsletter(
                newsletterProvider == null || newsletterProvider.isBlank()
                        ? "mailerlite" : newsletterProvider.trim().toLowerCase(),
                newsletterApiKey.map(String::trim).filter(s -> !s.isEmpty()),
                newsletterGroupId.map(String::trim).filter(s -> !s.isEmpty()),
                Math.max(1, newsletterRatePerIpPerHour));
    }

    public External external() {
        return new External(
                externalEnabled,
                externalOpenfigiKey.orElse(""),
                externalProxyMode,
                externalProxyHost.orElse(""),
                externalProxyPort,
                externalProxyUsername.orElse(""),
                externalProxyPassword.orElse(""),
                externalProxyNonProxyHosts.orElse(""),
                externalCacheDir,
                externalCacheTtlDays);
    }

    public record RateLimit(int perIpPerHour) {
    }

    public record UsageStats(Optional<String> ingestToken,
                             int ratePerIpPerHour,
                             Optional<String> geoipDbPath,
                             boolean dbConfigured) {
    }

    public record Report(int ttlMinutes) {
    }

    public record Newsletter(String provider,
                             Optional<String> apiKey,
                             Optional<String> groupId,
                             int ratePerIpPerHour) {
    }

    public record External(
            boolean enabled,
            String openfigiKey,
            String proxyMode,
            String proxyHost,
            int proxyPort,
            String proxyUsername,
            String proxyPassword,
            String proxyNonProxyHosts,
            String cacheDir,
            int cacheTtlDays
    ) {
    }
}
