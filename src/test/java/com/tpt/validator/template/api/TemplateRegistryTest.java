package com.tpt.validator.template.api;

import com.tpt.validator.spec.SpecCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRegistryTest {

    @BeforeEach
    void resetRegistry() {
        TemplateRegistry.clearForTesting();
        TemplateRegistry.init();
    }

    @Test
    void initRegistersTpt() {
        assertThat(TemplateRegistry.all())
                .extracting(TemplateDefinition::id)
                .contains(TemplateId.TPT);
    }

    @Test
    void tptLatestIsV7_0() {
        TemplateDefinition tpt = TemplateRegistry.of(TemplateId.TPT);
        assertThat(tpt.latest().version()).isEqualTo("V7.0");
    }

    @Test
    void tptSpecCatalogLoadsAtLeast140Fields() {
        TemplateDefinition tpt = TemplateRegistry.of(TemplateId.TPT);
        SpecCatalog catalog = tpt.specLoaderFor(tpt.latest()).load();
        assertThat(catalog.fields().size()).isGreaterThanOrEqualTo(140);
    }

    @Test
    void initIsIdempotent() {
        int before = TemplateRegistry.all().size();
        TemplateRegistry.init();
        TemplateRegistry.init();
        assertThat(TemplateRegistry.all()).hasSize(before);
    }

    @Test
    void unknownTemplateThrows() {
        TemplateRegistry.clearForTesting();
        assertThatThrownBy(() -> TemplateRegistry.of(TemplateId.TPT))
                .isInstanceOf(NoSuchElementException.class);
    }
}
