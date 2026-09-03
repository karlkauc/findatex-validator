package com.findatex.validator.ui;

import com.findatex.validator.validation.Finding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateTabControllerShowInSourceTest {

    @Test
    void rowAnchoredFindingCanBeShown() {
        Finding f = Finding.error("R", null, "12", "Field", 3, "x", "msg");
        assertThat(TemplateTabController.canShowInSource(TemplateTabController.FindingRow.of(f))).isTrue();
    }

    @Test
    void rowLevelFindingWithoutFieldCanBeShown() {
        Finding f = Finding.warn("XF", null, null, null, 3, null, "row-level");
        assertThat(TemplateTabController.canShowInSource(TemplateTabController.FindingRow.of(f))).isTrue();
    }

    @Test
    void globalFindingAndNoSelectionCannotBeShown() {
        Finding f = Finding.error("GLOBAL", null, null, null, null, null, "portfolio");
        assertThat(TemplateTabController.canShowInSource(TemplateTabController.FindingRow.of(f))).isFalse();
        assertThat(TemplateTabController.canShowInSource(null)).isFalse();
    }
}
