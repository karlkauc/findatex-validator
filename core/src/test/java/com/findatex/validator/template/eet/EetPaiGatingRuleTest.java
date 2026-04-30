package com.findatex.validator.template.eet;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateRuleSet;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.TestFileBuilder;
import com.findatex.validator.validation.ValidationContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.findatex.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAI gating: when NUM=33 ({@code 20100_..._Does_This_Product_Consider_Principle_Adverse_Impact})
 * is "Y", every NUM in the PAI block (103/104, 106..202) becomes mandatory.
 */
class EetPaiGatingRuleTest {

    /** Mirrors {@code EetRuleSet.PAI_BLOCK} — kept here as a regression guard. */
    private static final List<String> PAI_BLOCK = List.of(
            "103", "104",
            "106", "110", "114", "118", "122", "126", "130", "134", "138",
            "142", "146", "150", "154", "158", "162", "166", "170", "174",
            "178", "182", "186", "190", "194", "198", "202");

    static Stream<TemplateVersion> versions() {
        return Stream.of(EetTemplate.V1_1_3, EetTemplate.V1_1_2);
    }

    @ParameterizedTest
    @MethodSource("versions")
    void paiYesWithEmptyBlockEmitsErrorPerTarget(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_ENTITY);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "33", "Y"))
                .build();

        List<Finding> paiFindings = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .filter(f -> f.ruleId().startsWith("EET-XF-PAI-"))
                .toList();

        assertThat(paiFindings).hasSize(PAI_BLOCK.size());
        assertThat(paiFindings).extracting(Finding::severity).containsOnly(Severity.ERROR);
        assertThat(paiFindings).extracting(Finding::fieldNum)
                .containsExactlyInAnyOrderElementsOf(PAI_BLOCK);
    }

    @ParameterizedTest
    @MethodSource("versions")
    void paiNoFiresNothing(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_ENTITY);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "33", "N"))
                .build();

        boolean anyPai = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .anyMatch(f -> f.ruleId().startsWith("EET-XF-PAI-"));
        assertThat(anyPai).isFalse();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void paiYesWithFullBlockFiresNothing(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_ENTITY);

        Map<String, String> row = new LinkedHashMap<>();
        row.put("1", v.version());
        row.put("33", "Y");
        row.put("103", "Q");      // PAI snapshot frequency (closed list A/Q/M)
        row.put("104", "2025-12-31");  // PAI reference date
        for (int i = 2; i < PAI_BLOCK.size(); i++) {
            row.put(PAI_BLOCK.get(i), "Y");
        }

        TptFile file = new TestFileBuilder().row(row).build();

        boolean anyPai = rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .anyMatch(f -> f.ruleId().startsWith("EET-XF-PAI-"));
        assertThat(anyPai).isFalse();
    }
}
