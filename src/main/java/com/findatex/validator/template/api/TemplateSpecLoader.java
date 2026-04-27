package com.findatex.validator.template.api;

import com.findatex.validator.spec.SpecCatalog;

/** Loads the bundled spec for a single {@link TemplateVersion} into a {@link SpecCatalog}. */
public interface TemplateSpecLoader {
    SpecCatalog load();
}
