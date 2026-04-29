package com.findatex.validator.batch;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateVersion;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/** Static inputs for one {@link BatchValidationService} run. */
public record BatchValidationOptions(
        TemplateDefinition template,
        TemplateVersion version,
        Set<ProfileKey> activeProfiles,
        boolean externalValidationEnabled,
        AppSettings appSettings,
        Path externalCacheDir) {

    public BatchValidationOptions {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(version, "version");
        activeProfiles = Set.copyOf(Objects.requireNonNull(activeProfiles, "activeProfiles"));
        Objects.requireNonNull(appSettings, "appSettings");
        // externalCacheDir may be null when externalValidationEnabled is false.
    }
}
