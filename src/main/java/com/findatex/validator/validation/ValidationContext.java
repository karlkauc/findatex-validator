package com.findatex.validator.validation;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;

import java.util.Set;

public final class ValidationContext {

    private final TptFile file;
    private final SpecCatalog catalog;
    private final Set<ProfileKey> activeProfiles;

    public ValidationContext(TptFile file, SpecCatalog catalog, Set<ProfileKey> activeProfiles) {
        this.file = file;
        this.catalog = catalog;
        this.activeProfiles = Set.copyOf(activeProfiles);
    }

    public TptFile file() { return file; }
    public SpecCatalog catalog() { return catalog; }
    public Set<ProfileKey> activeProfiles() { return activeProfiles; }
}
