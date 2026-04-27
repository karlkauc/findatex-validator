package com.findatex.validator.template.api;

/**
 * Per-template regulatory profile identifier. Replaces the previous global
 * {@code com.findatex.validator.spec.Profile} enum so each template can carry its own profile set.
 */
public record ProfileKey(TemplateId templateId, String code, String displayName) {
}
