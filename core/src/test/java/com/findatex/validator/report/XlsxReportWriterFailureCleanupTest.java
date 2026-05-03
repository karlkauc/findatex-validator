package com.findatex.validator.report;

import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.template.tpt.TptTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XlsxReportWriterFailureCleanupTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    @Test
    void noPartialFileLeftBehindWhenSheetWriteFails(@TempDir Path tmp) {
        QualityReport broken = new QualityReport(
                null,           // file == null → applyWorkbookProperties → sourceFilename NPEs
                Set.of(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Instant.now());
        Path out = tmp.resolve("broken.xlsx");
        Path tmpFile = tmp.resolve("broken.xlsx.tmp");

        assertThatThrownBy(() ->
                new XlsxReportWriter(CATALOG, TptProfiles.ALL, TptTemplate.V7_0, GenerationUi.DESKTOP)
                        .write(broken, out))
                .isInstanceOf(NullPointerException.class);

        assertThat(Files.exists(out))
                .as("XlsxReportWriter must not leave a 0-byte file behind on failure")
                .isFalse();
        assertThat(Files.exists(tmpFile))
                .as("XlsxReportWriter must clean up the .tmp scratch file on failure")
                .isFalse();
    }
}
