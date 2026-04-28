package com.findatex.validator.external.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Authenticator;
import java.util.Optional;

public final class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private ProxyService() {}

    /** Must run before the first HTTP request. */
    public static void enableNtlmAuthentication() {
        SystemProxyDetector.clearProxyConfiguration();
        SystemProxyDetector.enableNtlmAuthentication();
        log.info("NTLM authentication enabled");
    }

    public static void clearJvmProxyProperties() {
        SystemProxyDetector.clearProxyConfiguration();
    }

    /** Apply user's selected mode. Call after settings change. */
    public static void applyMode(ProxyConfig cfg) {
        clearJvmProxyProperties();
        switch (cfg.mode()) {
            case SYSTEM -> applySystem();
            case MANUAL -> applyManual(cfg);
            case NONE   -> log.debug("Proxy mode NONE: properties cleared");
        }
    }

    private static void applySystem() {
        Optional<SystemProxyDetector.ProxyConfig> detected = SystemProxyDetector.detectSystemProxy();
        if (detected.isPresent()) {
            SystemProxyDetector.ProxyConfig p = detected.get();
            SystemProxyDetector.configureProxy(p.host(), p.port());
            log.info("System proxy applied: {}:{}", p.host(), p.port());
        } else {
            log.info("No static system proxy detected; relying on default ProxySelector (PAC/WPAD)");
        }
    }

    private static void applyManual(ProxyConfig cfg) {
        if (cfg.host().isEmpty() || cfg.port() <= 0) {
            log.warn("Manual proxy selected but host/port not set; treating as NONE");
            return;
        }
        SystemProxyDetector.configureProxy(cfg.host(), cfg.port(), cfg.nonProxyHosts());
        if (!cfg.user().isEmpty()) {
            Authenticator.setDefault(new ProxyAuthenticator(cfg.user(), cfg.password()));
            log.info("Manual proxy applied with credentials for user '{}'", cfg.user());
        } else {
            log.info("Manual proxy applied without credentials");
        }
    }
}
