package com.tpt.validator.validation;

import com.tpt.validator.domain.TptFile;
import com.tpt.validator.ingest.TptFileLoader;
import com.tpt.validator.report.QualityReport;
import com.tpt.validator.report.QualityScorer;
import com.tpt.validator.report.ScoreCategory;
import com.tpt.validator.spec.Profile;
import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.spec.SpecLoader;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    @Test
    void cleanXlsxYieldsHighScore() throws Exception {
        QualityReport r = run("/sample/clean_v7.xlsx",
                EnumSet.of(Profile.SOLVENCY_II, Profile.IORP_EIOPA_ECB, Profile.NW_675));

        assertThat(r.file().rows()).hasSize(3);
        assertThat(r.scores().get(ScoreCategory.OVERALL))
                .as("Overall score for clean sample should be high")
                .isGreaterThan(0.7);

        long errors = r.findings().stream()
                .filter(f -> f.severity() == Severity.ERROR)
                .filter(f -> f.ruleId().startsWith("FORMAT/"))
                .count();
        assertThat(errors).as("clean sample should not have format errors").isZero();
    }

    @Test
    void missingMandatoryProducesPresenceErrors() throws Exception {
        QualityReport r = run("/sample/missing_mandatory.csv",
                EnumSet.of(Profile.SOLVENCY_II));

        long missing = r.findings().stream()
                .filter(f -> f.severity() == Severity.ERROR)
                .filter(f -> f.ruleId().startsWith("PRESENCE/"))
                .count();
        assertThat(missing).as("missing mandatory sample should report presence errors")
                .isGreaterThanOrEqualTo(3);

        // The mandatory completeness score should drop below 100 %.
        assertThat(r.scores().get(ScoreCategory.MANDATORY_COMPLETENESS)).isLessThan(1.0);
    }

    @Test
    void currencyForwardXte2DoesNotTriggerInterestRateFindings() throws Exception {
        // Regression test: a currency forward (CIC XTE2 — class E, sub-cat 2) must NOT
        // produce findings for fields 32, 33 (sub-cat-restricted to E1 in the spec) or
        // field 34 (whose qualifier is purely the cross-field "if item 32 set to
        // Floating" — handled by XF-10, not by a generic ConditionalPresenceRule).
        com.tpt.validator.domain.TptFile file = new com.tpt.validator.validation.TestFileBuilder()
                .row(com.tpt.validator.validation.TestFileBuilder.values(
                        "1", "FR0010000001",
                        "3", "Demo Fund",
                        "6", "2025-12-31",
                        "7", "2025-12-31",
                        "12", "XTE2",
                        "14", "FX-FWD-EUR-USD-001",
                        "15", "99",
                        "17", "EUR/USD forward",
                        "21", "EUR",
                        "22", "0",
                        "23", "0",
                        "24", "0",
                        "25", "0",
                        "26", "0"))
                .build();

        java.util.List<Finding> findings = new ValidationEngine(CATALOG)
                .validate(file, EnumSet.of(Profile.SOLVENCY_II));

        // 32 and 33 are excluded by the sub-category whitelist (CICE = "x for E1").
        // 34 must be excluded by the XF-10 suppression in RuleRegistry.
        for (String fieldNum : new String[]{"32", "33", "34"}) {
            assertThat(findings)
                    .as("XTE2 must not trigger any presence/conditional finding for field %s", fieldNum)
                    .noneMatch(f -> ("PRESENCE/" + fieldNum + "/SOLVENCY_II").equals(f.ruleId())
                            || ("COND_PRESENCE/" + fieldNum + "/SOLVENCY_II").equals(f.ruleId()));
        }
    }

    @Test
    void sstProfileTriggersOwnPresenceFindings() throws Exception {
        // The clean sample is a minimal Solvency-II-shaped file. Activating SST alone
        // exposes SST-specific Mandatory fields that the sample doesn't fill, so we
        // expect a non-empty stream of PRESENCE/.../SST findings — and zero
        // SOLVENCY_II / NW_675 / IORP_EIOPA_ECB presence findings since those
        // profiles are not active.
        QualityReport r = run("/sample/clean_v7.xlsx", EnumSet.of(Profile.SST));
        long sstPresence = r.findings().stream()
                .filter(f -> f.severity() == Severity.ERROR)
                .filter(f -> f.ruleId().startsWith("PRESENCE/") && f.ruleId().endsWith("/SST"))
                .count();
        assertThat(sstPresence).isGreaterThan(0);

        long otherPresence = r.findings().stream()
                .filter(f -> f.ruleId().startsWith("PRESENCE/"))
                .filter(f -> !f.ruleId().endsWith("/SST"))
                .count();
        assertThat(otherPresence).isZero();
    }

    @Test
    void badFormatsAreFlagged() throws Exception {
        QualityReport r = run("/sample/bad_formats.xlsx",
                EnumSet.of(Profile.SOLVENCY_II));

        boolean badCurrency = r.findings().stream()
                .anyMatch(f -> f.ruleId().equals("FORMAT/21")
                        && f.value() != null && f.value().equalsIgnoreCase("ZZZ"));
        boolean badDate = r.findings().stream()
                .anyMatch(f -> f.ruleId().equals("FORMAT/6"));
        boolean badCoupon = r.findings().stream()
                .anyMatch(f -> f.ruleId().equals("XF-08/COUPON_FREQUENCY"));

        assertThat(badCurrency).as("invalid currency 'ZZZ' should be flagged").isTrue();
        assertThat(badDate).as("non-ISO date 31/12/2025 should be flagged").isTrue();
        assertThat(badCoupon).as("coupon frequency 3 should be flagged").isTrue();
    }

    private QualityReport run(String resourcePath, Set<Profile> profiles) throws Exception {
        URL url = EndToEndTest.class.getResource(resourcePath);
        assertThat(url).as("missing resource " + resourcePath).isNotNull();
        Path p = Path.of(url.toURI());
        TptFile file = new TptFileLoader(CATALOG).load(p);
        List<Finding> findings = new ValidationEngine(CATALOG).validate(file, profiles);
        return new QualityScorer(CATALOG).score(file, profiles, findings);
    }
}
