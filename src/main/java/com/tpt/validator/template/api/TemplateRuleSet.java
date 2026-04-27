package com.tpt.validator.template.api;

import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.validation.Rule;

import java.util.List;
import java.util.Set;

/** Builds the validation rule list for a given template version + selected profiles. */
public interface TemplateRuleSet {
    List<Rule> build(SpecCatalog catalog, Set<ProfileKey> selectedProfiles);
}
