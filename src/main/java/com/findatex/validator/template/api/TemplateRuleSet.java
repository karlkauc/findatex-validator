package com.findatex.validator.template.api;

import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.validation.Rule;

import java.util.List;
import java.util.Set;

/** Builds the validation rule list for a given template version + selected profiles. */
public interface TemplateRuleSet {
    List<Rule> build(SpecCatalog catalog, Set<ProfileKey> selectedProfiles);
}
