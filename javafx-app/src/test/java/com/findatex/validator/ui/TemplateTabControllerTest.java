package com.findatex.validator.ui;

import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateId;
import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.template.api.TemplateVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateTabControllerTest {

    @BeforeEach
    void initRegistry() {
        TemplateRegistry.init();
    }

    @Test
    void controllerExposesItsTemplate() {
        TemplateDefinition tpt = TemplateRegistry.of(TemplateId.TPT);
        TemplateTabController c = new TemplateTabController(tpt);
        assertThat(c.template()).isSameAs(tpt);
    }

    @Test
    void initialSelectedVersionIsLatest() {
        TemplateDefinition tpt = TemplateRegistry.of(TemplateId.TPT);
        TemplateTabController c = new TemplateTabController(tpt);
        assertThat(c.selectedVersion()).isEqualTo(tpt.latest());
    }

    @Test
    void unknownVersionRejected() {
        TemplateDefinition tpt = TemplateRegistry.of(TemplateId.TPT);
        TemplateTabController c = new TemplateTabController(tpt);
        TemplateVersion bogus = new TemplateVersion(
                TemplateId.TPT, "V99", "fake", "/nope.xlsx", "Sheet", null, null);
        assertThatThrownBy(() -> c.setSelectedVersion(bogus))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullTemplateRejected() {
        assertThatThrownBy(() -> new TemplateTabController(null))
                .isInstanceOf(NullPointerException.class);
    }
}
