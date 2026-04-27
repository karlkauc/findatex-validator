package com.findatex.validator.validation;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.eet.EetProfiles;
import com.findatex.validator.template.eet.EetTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-tests the EET V1.1.3 sample fixtures under {@code samples/eet/}.
 * Disabled if the generator hasn't been run.
 */
@EnabledIf("samplesPresent")
class EetExampleSamplesTest {

    private static final EetTemplate TEMPLATE = new EetTemplate();
    private static final SpecCatalog CATALOG =
            TEMPLATE.specLoaderFor(EetTemplate.V1_1_3).load();
    private static final ValidationEngine ENGINE =
            new ValidationEngine(CATALOG, TEMPLATE.ruleSetFor(EetTemplate.V1_1_3));
    private static final Set<ProfileKey> PROFILES = Set.of(EetProfiles.SFDR_PERIODIC);

    static boolean samplesPresent() {
        return Files.isDirectory(samplesDir());
    }

    private static Path samplesDir() {
        return Paths.get("").toAbsolutePath().resolve("samples").resolve("eet");
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

    @Test
    void sfdrArt8MissingMinFlagged() throws Exception {
        List<Finding> findings = run("04_sfdr_art8_no_min.xlsx");
        boolean ltMissing = findings.stream().anyMatch(f -> f.ruleId().equals("EET-XF-ART8-MIN-LT"));
        assertThat(ltMissing).as("EET-XF-ART8-MIN-LT must fire").isTrue();
    }

    @Test
    void sfdrArt9MissingMinFlagged() throws Exception {
        List<Finding> findings = run("05_sfdr_art9_no_min.xlsx");
        boolean ltMissing = findings.stream().anyMatch(f -> f.ruleId().equals("EET-XF-ART9-MIN-LT"));
        assertThat(ltMissing).as("EET-XF-ART9-MIN-LT must fire").isTrue();
    }

    private List<Finding> run(String filename) throws Exception {
        Path p = samplesDir().resolve(filename);
        assertThat(Files.exists(p)).as("missing sample %s", p).isTrue();
        TptFile file = new TptFileLoader(CATALOG).load(p);
        return ENGINE.validate(file, PROFILES);
    }
}
