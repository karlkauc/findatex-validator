package com.findatex.validator.validation;

import com.findatex.validator.template.ept.EptProfiles;
import com.findatex.validator.template.ept.EptTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-tests the EPT V2.1 sample fixtures under {@code samples/ept/}.
 * Disabled if the generator hasn't been run.
 *
 * <p>Uses {@link TemplateSampleHarness} to drive {@link EptTemplate}'s rule
 * set directly — see that class's Javadoc for the architectural reason.</p>
 */
@EnabledIf("samplesPresent")
class EptExampleSamplesTest {

    private static final TemplateSampleHarness HARNESS = new TemplateSampleHarness(
            new EptTemplate(), EptTemplate.V2_1, Set.of(EptProfiles.PRIIPS_KID));

    static boolean samplesPresent() {
        return Files.isDirectory(samplesDir());
    }

    private static Path samplesDir() {
        return Paths.get("").toAbsolutePath().resolve("samples").resolve("ept");
    }

    @Test
    void cleanFileHasNoFormatErrors() throws Exception {
        List<Finding> findings = run("01_clean.xlsx");
        long fmt = findings.stream()
                .filter(f -> f.severity() == Severity.ERROR)
                .filter(f -> f.ruleId().startsWith("FORMAT/"))
                .count();
        assertThat(fmt).as("01_clean must have zero FORMAT/* errors").isZero();
    }

    @Test
    void missingMandatoryFlagged() throws Exception {
        List<Finding> findings = run("02_missing_mandatory.xlsx");
        long presence = findings.stream()
                .filter(f -> f.severity() == Severity.ERROR)
                .filter(f -> f.ruleId().startsWith("PRESENCE/"))
                .count();
        assertThat(presence).as("expected at least 3 PRESENCE/* errors").isGreaterThanOrEqualTo(3);
    }

    @Test
    void badFormatsFlagged() throws Exception {
        List<Finding> findings = run("03_bad_formats.xlsx");
        boolean any = findings.stream().anyMatch(f -> f.ruleId().startsWith("FORMAT/"));
        assertThat(any).as("at least one FORMAT/* error").isTrue();
    }

    private List<Finding> run(String filename) throws Exception {
        Path p = samplesDir().resolve(filename);
        assertThat(Files.exists(p)).as("missing sample %s", p).isTrue();
        return HARNESS.run(p);
    }
}
