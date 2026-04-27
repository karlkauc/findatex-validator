package com.findatex.validator.template.api;

import java.time.LocalDate;

/**
 * Single (template, version) coordinate plus the metadata needed to load the bundled spec.
 * {@code resourcePath} and {@code manifestResource} are classpath resources (slash-prefixed).
 */
public record TemplateVersion(
        TemplateId templateId,
        String version,
        String label,
        String resourcePath,
        String sheetName,
        LocalDate releaseDate,
        String manifestResource) {
}
