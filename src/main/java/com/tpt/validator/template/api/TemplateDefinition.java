package com.tpt.validator.template.api;

import java.util.List;

/**
 * Describes one FinDatEx template (TPT, EET, EMT, EPT) and exposes its versions, profiles,
 * and per-version spec/rule providers. Implementations live under {@code com.tpt.validator.template.<id>/}.
 */
public interface TemplateDefinition {

    TemplateId id();

    String displayName();

    /** Versions ordered most-recent first. {@link #latest()} returns {@code versions().get(0)}. */
    List<TemplateVersion> versions();

    default TemplateVersion latest() {
        List<TemplateVersion> v = versions();
        if (v.isEmpty()) {
            throw new IllegalStateException("Template " + id() + " has no registered versions");
        }
        return v.get(0);
    }

    ProfileSet profiles();

    TemplateSpecLoader specLoaderFor(TemplateVersion version);

    TemplateRuleSet ruleSetFor(TemplateVersion version);
}
