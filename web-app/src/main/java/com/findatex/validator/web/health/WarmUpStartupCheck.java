package com.findatex.validator.web.health;

import com.findatex.validator.web.service.ValidationOrchestrator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Startup;

/**
 * {@code GET /_internal/health/started} stays DOWN until {@link ValidationOrchestrator#warmUp()}
 * has finished. Cloud Run's HTTP startup probe points here, so a fresh instance only
 * receives traffic once the warm-up ran — during startup it has full (boosted) CPU,
 * afterwards a background thread is throttled to near zero between requests.
 *
 * <p>Safety valve: after {@link #MAX_WAIT_MS} the check reports UP regardless, so a
 * stuck warm-up can delay an instance but never keep it from serving.
 */
@Startup
@ApplicationScoped
public class WarmUpStartupCheck implements HealthCheck {

    static final long MAX_WAIT_MS = 60_000;

    private final long createdAt = System.currentTimeMillis();

    @Inject
    ValidationOrchestrator orchestrator;

    @ConfigProperty(name = "findatex.web.warmup.enabled", defaultValue = "true")
    boolean warmUpEnabled;

    @Override
    public HealthCheckResponse call() {
        boolean up = !warmUpEnabled
                || orchestrator.warmedUp()
                || System.currentTimeMillis() - createdAt > MAX_WAIT_MS;
        return HealthCheckResponse.named("warm-up").status(up).build();
    }
}
