package com.findatex.validator.web.service;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.external.ExternalValidationService;
import com.findatex.validator.web.config.WebConfig;
import com.findatex.validator.web.dto.ExternalOptions;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Hybrid factory for {@link ExternalValidationService}.
 *
 * <p>Holds a single shared instance bound to the operator-supplied env-default OpenFIGI
 * key — that one is reused across all requests so the OpenFIGI rate limiter is global.
 * For requests where the user supplies their own OpenFIGI key the factory hands out a
 * transient instance; the user key never leaks into a singleton, map or cache key.
 */
@ApplicationScoped
public class ExternalValidationFactory {

    private static final Logger log = LoggerFactory.getLogger(ExternalValidationFactory.class);

    @Inject
    WebConfig config;

    private Path cacheDir;
    private volatile ExternalValidationService defaultService;

    @PostConstruct
    void init() {
        if (!config.external().enabled()) {
            log.info("External validation disabled (findatex.web.external.enabled=false)");
            return;
        }
        try {
            cacheDir = Path.of(config.external().cacheDir());
            Files.createDirectories(cacheDir);
            defaultService = ExternalValidationService.forProduction(
                    cacheDir,
                    new AppSettings.Isin(true, config.external().openfigiKey(), true, true));
            log.info("External validation factory ready (cache-dir={}, env-key={})",
                    cacheDir, config.external().openfigiKey().isEmpty() ? "absent" : "present");
        } catch (IOException e) {
            log.error("Failed to prepare external validation cache directory '{}': {}",
                    config.external().cacheDir(), e.getMessage());
            defaultService = null;
        }
    }

    /**
     * Returns a service handle. With no user key (or an empty/blank one) the shared
     * default service is returned; otherwise a transient service bound to the user key
     * is built. The handle's {@link ServiceHandle#close()} is a no-op — it exists so
     * callers can use try-with-resources to make the request scope explicit.
     */
    public ServiceHandle resolve(Optional<String> userKey) {
        if (defaultService == null && userKey.isEmpty()) {
            return new ServiceHandle(null, false);
        }
        String key = userKey.map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
        if (key == null) {
            return new ServiceHandle(defaultService, false);
        }
        ExternalValidationService transientService = ExternalValidationService.forProduction(
                cacheDir,
                new AppSettings.Isin(true, key, true, true));
        return new ServiceHandle(transientService, true);
    }

    /**
     * Build the {@link AppSettings} the service consumes from the per-request user
     * choices. The effective OpenFIGI key is the user key if present, else the env
     * default — never persisted, never logged.
     */
    public AppSettings buildSettings(ExternalOptions opts) {
        String effectiveKey = opts.userOpenfigiKey()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(config.external().openfigiKey());
        Path dir = cacheDir != null ? cacheDir : Path.of(config.external().cacheDir());
        return new AppSettings(
                new AppSettings.External(
                        true,
                        new AppSettings.Lei(opts.leiEnabled(), opts.leiCheckLapsed(),
                                opts.leiCheckName(), opts.leiCheckCountry()),
                        new AppSettings.Isin(opts.isinEnabled(), effectiveKey,
                                opts.isinCheckCurrency(), opts.isinCheckCic()),
                        new AppSettings.Cache(config.external().cacheTtlDays(),
                                dir.toString())),
                new AppSettings.Proxy(AppSettings.ProxyMode.NONE,
                        new AppSettings.ManualProxy("", 0, "", "", "")));
    }

    public boolean enabled() {
        return defaultService != null;
    }

    public record ServiceHandle(ExternalValidationService service, boolean transientService)
            implements AutoCloseable {
        @Override
        public void close() {
            // Nothing to release explicitly. The transient service (if any) is GCed
            // along with its rate limiter and the user key it captured. Keeping the
            // try-with-resources usage at the call site documents request-scope intent.
        }
    }
}
