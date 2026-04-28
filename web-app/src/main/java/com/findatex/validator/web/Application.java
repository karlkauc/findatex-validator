package com.findatex.validator.web;

import com.findatex.validator.template.api.TemplateRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Eagerly initialises the shared {@link TemplateRegistry} at application start
 * so the first request doesn't pay the spec-loading cost. The registry is a
 * process-wide singleton that all web requests share read-only.
 */
@Startup
@ApplicationScoped
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public Application() {
        TemplateRegistry.init();
        log.info("FinDatEx web app started. Templates registered: {}", TemplateRegistry.all().size());
    }
}
