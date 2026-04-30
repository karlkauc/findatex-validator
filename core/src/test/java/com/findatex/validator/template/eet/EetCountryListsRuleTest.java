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

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.findatex.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Country-list gating: when NUM=225 ({@code 31210_..._Subject_To_Social_Violations_Value},
 * Integer count) is &gt; 0, NUM=615 ({@code 100000_List_Of_Countries_Subject_To_Social_Violations})
 * must be present. When NUM=228 ({@code 31240_..._Eligible_Assets}, floating decimal
 * proportion) is &gt; 0, NUM=616 ({@code 100010_List_Of_Invested_Countries}) must be
 * present. Comment text "Blank if none; conditional to 31210 &gt; 0" / "31240 &gt; 0" is
 * identical in V1.1.2 and V1.1.3 — V1.1.2 has C-flag on the targets, V1.1.3 dropped
 * the flag, but the cross-field rule fires in both versions. Severity = ERROR (no
 * softening clause in the spec).
 */
class EetCountryListsRuleTest {

    static Stream<TemplateVersion> versions() {
        return Stream.of(EetTemplate.V1_1_3, EetTemplate.V1_1_2);
    }

    @ParameterizedTest
    @MethodSource("versions")
    void violationsCountAboveZeroFires615(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_ENTITY);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "225", "3"))
                .build();

        List<Finding> hits = countryListFindings(rs, catalog, file, profiles);
        assertThat(hits).extracting(Finding::ruleId).containsExactly("EET-XF-COUNTRYLIST-615");
        assertThat(hits).extracting(Finding::severity).containsOnly(Severity.ERROR);
        assertThat(hits).extracting(Finding::fieldNum).containsExactly("615");
    }

    @ParameterizedTest
    @MethodSource("versions")
    void eligibleAssetsAboveZeroFires616(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_ENTITY);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "228", "0.4"))
                .build();

        List<Finding> hits = countryListFindings(rs, catalog, file, profiles);
        assertThat(hits).extracting(Finding::ruleId).containsExactly("EET-XF-COUNTRYLIST-616");
        assertThat(hits).extracting(Finding::fieldNum).containsExactly("616");
    }

    @ParameterizedTest
    @MethodSource("versions")
    void bothTriggersFireBothFindings(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_ENTITY);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "225", "5", "228", "0.7"))
                .build();

        List<Finding> hits = countryListFindings(rs, catalog, file, profiles);
        assertThat(hits).extracting(Finding::ruleId)
                .containsExactlyInAnyOrder("EET-XF-COUNTRYLIST-615", "EET-XF-COUNTRYLIST-616");
    }

    @ParameterizedTest
    @MethodSource("versions")
    void zeroValuesFireNothing(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_ENTITY);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "225", "0", "228", "0"))
                .build();

        assertThat(countryListFindings(rs, catalog, file, profiles)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void blankTriggersFireNothing(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_ENTITY);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version()))
                .build();

        assertThat(countryListFindings(rs, catalog, file, profiles)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("versions")
    void triggerWithCommaDecimalFires(TemplateVersion v) {
        // EU-locale data sometimes surfaces "0,4" rather than "0.4".
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_ENTITY);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(), "228", "0,4"))
                .build();

        List<Finding> hits = countryListFindings(rs, catalog, file, profiles);
        assertThat(hits).extracting(Finding::ruleId).containsExactly("EET-XF-COUNTRYLIST-616");
    }

    @ParameterizedTest
    @MethodSource("versions")
    void triggerWithTargetPopulatedFiresNothing(TemplateVersion v) {
        EetTemplate eet = new EetTemplate();
        SpecCatalog catalog = eet.specLoaderFor(v).load();
        TemplateRuleSet rs = eet.ruleSetFor(v);
        Set<ProfileKey> profiles = Set.of(EetProfiles.SFDR_ENTITY);

        TptFile file = new TestFileBuilder()
                .row(values("1", v.version(),
                        "225", "3", "615", "MM;IQ",
                        "228", "0.7", "616", "MM;IQ;GE;FR"))
                .build();

        assertThat(countryListFindings(rs, catalog, file, profiles)).isEmpty();
    }

    private static List<Finding> countryListFindings(TemplateRuleSet rs, SpecCatalog catalog,
                                                     TptFile file, Set<ProfileKey> profiles) {
        return rs.build(catalog, profiles).stream()
                .flatMap(r -> r.evaluate(new ValidationContext(file, catalog, profiles)).stream())
                .filter(f -> f.ruleId().startsWith("EET-XF-COUNTRYLIST-"))
                .toList();
    }
}
