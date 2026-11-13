package com.findatex.validator.web.service;

import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.template.api.TemplateVersion;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ValidationOrchestratorWarmUpTest {

    @Inject
    ValidationOrchestrator orchestrator;

    @Test
    void warmUpBuildsTheCatalogOfEveryTemplateVersion() {
        orchestrator.warmUp();

        assertThat(orchestrator.warmedUp()).isTrue();
        for (TemplateDefinition def : TemplateRegistry.all()) {
            for (TemplateVersion v : def.versions()) {
                // Older versions too: TPT V7.0 is still uploaded more often than V8.0.
                assertThat(orchestrator.cachedBundles()).contains(def.id() + "/" + v.version());
            }
        }
    }
}
