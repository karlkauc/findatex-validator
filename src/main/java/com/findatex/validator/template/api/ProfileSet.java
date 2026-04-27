package com.findatex.validator.template.api;

import java.util.List;
import java.util.Optional;

/** Ordered, immutable container of {@link ProfileKey}s belonging to one template. */
public final class ProfileSet {

    private final TemplateId templateId;
    private final List<ProfileKey> all;

    public ProfileSet(TemplateId templateId, List<ProfileKey> all) {
        this.templateId = templateId;
        this.all = List.copyOf(all);
    }

    public TemplateId templateId() {
        return templateId;
    }

    public List<ProfileKey> all() {
        return all;
    }

    public Optional<ProfileKey> byCode(String code) {
        return all.stream().filter(p -> p.code().equals(code)).findFirst();
    }
}
