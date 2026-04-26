package com.tpt.validator.validation;

import com.tpt.validator.domain.TptFile;
import com.tpt.validator.spec.Profile;
import com.tpt.validator.spec.SpecCatalog;

import java.util.Set;

public final class ValidationContext {

    private final TptFile file;
    private final SpecCatalog catalog;
    private final Set<Profile> activeProfiles;

    public ValidationContext(TptFile file, SpecCatalog catalog, Set<Profile> activeProfiles) {
        this.file = file;
        this.catalog = catalog;
        this.activeProfiles = Set.copyOf(activeProfiles);
    }

    public TptFile file() { return file; }
    public SpecCatalog catalog() { return catalog; }
    public Set<Profile> activeProfiles() { return activeProfiles; }
}
