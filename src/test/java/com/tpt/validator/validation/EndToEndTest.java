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
