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

    public External external() {
        return new External(
                externalEnabled,
                externalOpenfigiKey.orElse(""),
                externalProxyMode,
                externalProxyHost.orElse(""),
                externalProxyPort,
                externalProxyUsername.orElse(""),
                externalProxyPassword.orElse(""),
                externalProxyNonProxyHosts.orElse(""));
    }

    public record RateLimit(int perIpPerHour) {
    }

    public record Report(int ttlMinutes) {
    }

    public record External(
            boolean enabled,
            String openfigiKey,
            String proxyMode,
            String proxyHost,
            int proxyPort,
            String proxyUsername,
            String proxyPassword,
            String proxyNonProxyHosts
    ) {
    }
}
