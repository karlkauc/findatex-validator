package com.findatex.validator.batch;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.template.tpt.TptTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BatchValidationServiceTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();
    private static final Set<ProfileKey> PROFILES = Set.of(
            TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB,
            TptProfiles.NW_675, TptProfiles.SST);

    @Test
    void validatesEveryFileInOrder(@TempDir Path folder) throws Exception {
        copySample("/sample/clean_v7.xlsx", folder.resolve("clean_v7.xlsx"));
        copySample("/sample/bad_formats.xlsx", folder.resolve("bad_formats.xlsx"));

        BatchSummary summary = newService().run(
                List.of(folder.resolve("bad_formats.xlsx"),
                        folder.resolve("clean_v7.xlsx")),
                () -> false);

        assertThat(summary.results()).hasSize(2);
        assertThat(summary.results().get(0).displayName()).isEqualTo("bad_formats.xlsx");
        assertThat(summary.results().get(1).displayName()).isEqualTo("clean_v7.xlsx");
        assertThat(summary.results()).allMatch(r -> r.status() == BatchFileStatus.OK);
        assertThat(summary.aggregateOverallScore()).isPresent();
        assertThat(summary.cancelled()).isFalse();
    }

    @Test
    void recordsLoadErrorWithoutAbortingTheRun(@TempDir Path folder) throws Exception {
        Path bad = folder.resolve("not-a-spreadsheet.xlsx");
        Files.writeString(bad, "this is not a real xlsx workbook");
        copySample("/sample/clean_v7.xlsx", folder.resolve("clean_v7.xlsx"));

        BatchSummary summary = newService().run(
                List.of(bad, folder.resolve("clean_v7.xlsx")),
                () -> false);

        assertThat(summary.results()).hasSize(2);
        assertThat(summary.results().get(0).status()).isEqualTo(BatchFileStatus.LOAD_ERROR);
        assertThat(summary.results().get(0).errorMessage()).isNotBlank();
        assertThat(summary.results().get(1).status()).isEqualTo(BatchFileStatus.OK);
    }

    @Test
    void cancellationStopsBetweenFiles(@TempDir Path folder) throws Exception {
        copySample("/sample/clean_v7.xlsx", folder.resolve("a.xlsx"));
        copySample("/sample/clean_v7.xlsx", folder.resolve("b.xlsx"));
        copySample("/sample/clean_v7.xlsx", folder.resolve("c.xlsx"));

        AtomicBoolean cancel = new AtomicBoolean(false);
        AtomicInteger completed = new AtomicInteger(0);
        BatchValidationService.Listener listener = new BatchValidationService.Listener() {
            @Override public void onFileComplete(BatchResult r) {
                if (completed.incrementAndGet() >= 1) cancel.set(true);
            }
        };

        BatchSummary summary = newService().run(
                List.of(folder.resolve("a.xlsx"),
                        folder.resolve("b.xlsx"),
                        folder.resolve("c.xlsx")),
                cancel::get, listener,
                com.findatex.validator.external.ExternalValidationService.ProgressSink.NOOP);

        assertThat(summary.cancelled()).isTrue();
        assertThat(summary.results()).hasSizeLessThan(3);
    }

    @Test
    void aggregatesFindingCountsAcrossFiles(@TempDir Path folder) throws Exception {
        copySample("/sample/bad_formats.xlsx", folder.resolve("bad.xlsx"));
        copySample("/sample/clean_v7.xlsx", folder.resolve("clean.xlsx"));

        BatchSummary summary = newService().run(
                List.of(folder.resolve("bad.xlsx"), folder.resolve("clean.xlsx")),
                () -> false);

        long expectedErrors = summary.results().stream()
                .flatMap(r -> r.findings().stream())
                .filter(f -> f.severity() == com.findatex.validator.validation.Severity.ERROR)
                .count();
        assertThat(summary.aggregateErrors()).isEqualTo(expectedErrors);
    }

    @Test
    void emitsProgressForEveryFile(@TempDir Path folder) throws Exception {
        copySample("/sample/clean_v7.xlsx", folder.resolve("a.xlsx"));
        copySample("/sample/clean_v7.xlsx", folder.resolve("b.xlsx"));

        List<BatchProgress.Phase> seen = new ArrayList<>();
        BatchValidationService.Listener listener = new BatchValidationService.Listener() {
            @Override public void onProgress(BatchProgress p) { seen.add(p.phase()); }
        };

        newService().run(List.of(folder.resolve("a.xlsx"), folder.resolve("b.xlsx")),
                () -> false, listener,
                com.findatex.validator.external.ExternalValidationService.ProgressSink.NOOP);

        assertThat(seen).contains(BatchProgress.Phase.LOADING,
                BatchProgress.Phase.VALIDATING,
                BatchProgress.Phase.SCORING,
                BatchProgress.Phase.DONE);
    }

    private BatchValidationService newService() {
        BatchValidationOptions opts = new BatchValidationOptions(
                new TptTemplate(),
                TptTemplate.V7_0,
                PROFILES,
                false,
                AppSettings.defaults(),
                null);
        return new BatchValidationService(CATALOG, opts);
    }

    private static void copySample(String resource, Path target) throws Exception {
        URL url = BatchValidationServiceTest.class.getResource(resource);
        assertThat(url).as("missing test resource %s", resource).isNotNull();
        Files.copy(Path.of(url.toURI()), target, StandardCopyOption.REPLACE_EXISTING);
    }
}
