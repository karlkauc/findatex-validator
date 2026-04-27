package com.tpt.validator.validation;

import com.tpt.validator.domain.CicCode;
import com.tpt.validator.domain.TptFile;
import com.tpt.validator.domain.TptRow;
import com.tpt.validator.ingest.TptFileLoader;
import com.tpt.validator.template.api.ProfileKey;
import com.tpt.validator.template.tpt.TptProfiles;
import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.spec.SpecLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test pinned to the production file the user reported. The file is held outside
 * the repository (could be confidential) so the test is conditionally enabled.
 *
 * <p>The reported bug was: position with CIC {@code BE21} (Belgian sovereign bond
 * sub-category 1) triggered a {@code COND_PRESENCE/18} warning even though field 18
 * (Quantity) is conditional only for sub-categories {@code 22} and {@code 29} within CIC 2.
 */
@EnabledIf("userFilePresent")
class UserFileVerificationTest {

    private static final Path USER_FILE = Path.of(
            "/home/karl/webdav/tpt_test/20260331_TPTV7_CZ0008472271_2026-03-31.xlsx");

    @SuppressWarnings("unused")
    static boolean userFilePresent() {
        return Files.exists(USER_FILE);
    }

    @Test
    void noQuantityWarningOnBE21Position() throws Exception {
        SpecCatalog catalog = SpecLoader.loadBundled();
        TptFile file = new TptFileLoader(catalog).load(USER_FILE);
        List<Finding> findings = new ValidationEngine(catalog)
                .validate(file, Set.of(TptProfiles.SOLVENCY_II));

        long be21Warnings = findings.stream()
                .filter(f -> "COND_PRESENCE/18/SOLVENCY_II".equals(f.ruleId()))
                .filter(f -> f.rowIndex() != null)
                .filter(f -> rowCic(file, f.rowIndex())
                        .map(c -> c.categoryDigit().equals("2") && c.subcategory().equals("1"))
                        .orElse(false))
                .count();

        assertThat(be21Warnings)
                .as("Field 18 must not flag CIC %s2%s1 — restricted to sub-categories 22 / 29",
                        "x", "x")
                .isZero();
    }

    private static java.util.Optional<CicCode> rowCic(TptFile file, int rowIndex) {
        for (TptRow r : file.rows()) {
            if (r.rowIndex() == rowIndex) return r.cic();
        }
        return java.util.Optional.empty();
    }
}
