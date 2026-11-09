package com.findatex.validator.web;

import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.web.service.SampleFiles;
import com.findatex.validator.web.service.ValidationOrchestrator;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Eagerly initialises the shared {@link TemplateRegistry} at application start.
 * The registry is a process-wide singleton that all web requests share
 * read-only. Registering does not parse any spec workbook; a background warm-up
 * ({@link ValidationOrchestrator#warmUp()}, {@code findatex.web.warmup.enabled})
 * then builds the latest version of each template and runs its sample once.
 *
 * <p>Also reports missing demo files. They are mounted onto the classpath from
 * {@code samples/} by the build, and a build context without that directory
 * (the container image, until the {@code COPY} was added) produces an image
 * that works in every other respect while the "try an example" action quietly
 * disappears. A line in the startup log is the difference between noticing
 * that and not.
 */
@ApplicationScoped
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    @Inject
    ValidationOrchestrator orchestrator;

    @ConfigProperty(name = "findatex.web.warmup.enabled", defaultValue = "true")
    boolean warmUpEnabled;

    /*
     * A startup observer, not the constructor: the constructor of a normal-scoped bean also
     * runs for its client proxy, which initialised the registry and logged this line twice.
     */
    void onStart(@Observes StartupEvent ev) {
        TemplateRegistry.init();
        log.info("FinDatEx web app started. Templates registered: {}", TemplateRegistry.all().size());
        warnAboutMissingSamples();
        if (warmUpEnabled) {
            // Off the main thread so Quarkus finishes booting; the startup probe
            // (/_internal/health/started, WarmUpStartupCheck) holds traffic back until it is done,
            // which is also the phase in which Cloud Run grants full (boosted) CPU.
            Thread t = new Thread(orchestrator::warmUp, "findatex-warmup");
            t.setDaemon(true);
            t.start();
        }
    }

    private static void warnAboutMissingSamples() {
        var missing = SampleFiles.declared().stream()
                .filter(s -> !s.exists())
                .map(s -> s.templateId() + " " + s.version() + " (" + s.resource() + ")")
                .toList();
        if (missing.isEmpty()) return;
        log.warn("Demo file missing for {} — the \"try an example\" action is not offered "
                + "for those template versions. The build did not put them on the classpath "
                + "(check the <resources> include in web-app/pom.xml, the .dockerignore "
                + "negation and the COPY in the Dockerfile).", missing);
    }
}
