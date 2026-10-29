package com.findatex.validator.web;

import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.web.service.SampleFiles;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Eagerly initialises the shared {@link TemplateRegistry} at application start.
 * The registry is a process-wide singleton that all web requests share
 * read-only. Registering does not parse any spec workbook — that happens on
 * the first validation per template and version ({@code ValidationOrchestrator}).
 *
 * <p>Also reports missing demo files. They are mounted onto the classpath from
 * {@code samples/} by the build, and a build context without that directory
 * (the container image, until the {@code COPY} was added) produces an image
 * that works in every other respect while the "try an example" action quietly
 * disappears. A line in the startup log is the difference between noticing
 * that and not.
 */
@Startup
@ApplicationScoped
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public Application() {
        TemplateRegistry.init();
        log.info("FinDatEx web app started. Templates registered: {}", TemplateRegistry.all().size());
        warnAboutMissingSamples();
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
