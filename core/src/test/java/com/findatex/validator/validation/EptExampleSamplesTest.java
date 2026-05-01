package com.findatex.validator.validation;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
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
 */
@EnabledIf("samplesPresent")
class EptExampleSamplesTest {

    private static final EptTemplate TEMPLATE = new EptTemplate();
    private static final SpecCatalog CATALOG =
            TEMPLATE.specLoaderFor(EptTemplate.V2_1).load();
    private static final ValidationEngine ENGINE =
            new ValidationEngine(CATALOG, TEMPLATE.ruleSetFor(EptTemplate.V2_1));
    private static final Set<ProfileKey> PROFILES = Set.of(EptProfiles.PRIIPS_KID);

    static boolean samplesPresent() {
        return Files.isDirectory(samplesDir());
    }

    /**
     * Resolve {@code samples/ept/} from the JVM's CWD. When Surefire launches
     * the test JVM with {@code core/} as CWD (the multi-module default) we walk
     * up to find the project root that actually owns {@code samples/}.
     */
    private static Path samplesDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path direct = cwd.resolve("samples").resolve("ept");
        if (Files.isDirectory(direct)) return direct;
        Path parent = cwd.getParent();
        if (parent != null) {
            Path up = parent.resolve("samples").resolve("ept");
            if (Files.isDirectory(up)) return up;
        }
        return direct;
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
        TptFile file = new TptFileLoader(CATALOG).load(p);
        return ENGINE.validate(file, PROFILES);
    }
}
