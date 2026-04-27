package com.tpt.validator.external;

import com.tpt.validator.config.AppSettings;
import com.tpt.validator.domain.TptFile;
import com.tpt.validator.external.gleif.LeiRecord;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Severity;
import com.tpt.validator.validation.TestFileBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.tpt.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

class ExternalValidationServiceTest {

    @Test
    void emitsExistenceFindingForUnknownLei(@TempDir Path tmp) {
        TptFile file = new TestFileBuilder()
                .row(values("47", "529900D6BF99LW9R2E68", "48", "1",
                            "46", "Some Issuer", "52", "DE"))
                .build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp,
                leis -> Map.of(),
                isins -> Map.of());

        List<Finding> out = svc.run(file, settings, () -> false);
        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE/47/48");
        assertThat(out).extracting(Finding::severity).contains(Severity.ERROR);
    }

    @Test
    void emitsServiceUnavailableInfoOnException(@TempDir Path tmp) {
        TptFile file = new TestFileBuilder()
                .row(values("47", "529900D6BF99LW9R2E68", "48", "1"))
                .build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        Function<List<String>, Map<String, LeiRecord>> failing = leis -> {
            throw new RuntimeException("boom");
        };
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp, failing, isins -> Map.of());
        List<Finding> out = svc.run(file, settings, () -> false);
        assertThat(out).extracting(Finding::ruleId).contains("EXTERNAL/GLEIF-UNAVAILABLE");
    }
}
