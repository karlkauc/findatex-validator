package com.findatex.validator.validation;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateRuleSet;
import com.findatex.validator.template.api.TemplateVersion;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Test harness that loads a sample fixture and runs one template's
 * {@link TemplateRuleSet} against it directly. Bypasses
 * {@link ValidationEngine}, which currently dispatches via
 * {@link RuleRegistry} — that registry is hard-wired to the TPT rule set,
 * so non-TPT XF rules never fire through the engine path.
 *
 * <p>The Javadoc on {@code RuleRegistry} flags this as a known TODO ("Will
 * be removed once ValidationEngine obtains its rules directly from a
 * TemplateRuleSet during the controller-level template handoff in
 * Phase 1"). When that lands, this harness can delegate to the engine or
 * be removed entirely; until then the per-template ExampleSamplesTest
 * classes use it so EET/EMT/EPT XF rules can be exercised in tests.</p>
 */
final class TemplateSampleHarness {

    private final SpecCatalog catalog;
    private final TemplateRuleSet ruleSet;
    private final Set<ProfileKey> profiles;

    TemplateSampleHarness(TemplateDefinition template,
                          TemplateVersion version,
                          Set<ProfileKey> profiles) {
        this.catalog = template.specLoaderFor(version).load();
        this.ruleSet = template.ruleSetFor(version);
        this.profiles = Set.copyOf(profiles);
    }

    SpecCatalog catalog() {
        return catalog;
    }

    List<Finding> run(Path samplePath) throws Exception {
        TptFile file = new TptFileLoader(catalog).load(samplePath);
        ValidationContext ctx = new ValidationContext(file, catalog, profiles);
        List<Finding> findings = new ArrayList<>();
        for (Rule r : ruleSet.build(catalog, profiles)) {
            findings.addAll(r.evaluate(ctx));
        }
        return findings;
    }
}
