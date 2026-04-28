package com.findatex.validator.validation;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateRuleSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(ValidationEngine.class);

    private final SpecCatalog catalog;
    private final TemplateRuleSet ruleSet;

    public ValidationEngine(SpecCatalog catalog, TemplateRuleSet ruleSet) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet");
    }

    public List<Finding> validate(TptFile file, Set<ProfileKey> activeProfiles) {
        List<Rule> rules = ruleSet.build(catalog, activeProfiles);
        ValidationContext ctx = new ValidationContext(file, catalog, activeProfiles);
        List<Finding> findings = new ArrayList<>();
        for (Rule r : rules) {
            try {
                findings.addAll(r.evaluate(ctx));
            } catch (Exception e) {
                log.warn("Rule {} threw {}: {}", r.id(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
        // Enrich with portfolio + position context so reports / UI can show fund name,
        // ISIN, valuation date and (per-row) the affected instrument and its weight.
        findings = FindingEnricher.enrich(file, findings);
        log.info("Validation produced {} findings ({} rules, {} rows)",
                findings.size(), rules.size(), file.rows().size());
        return findings;
    }
}
