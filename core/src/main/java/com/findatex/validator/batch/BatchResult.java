package com.findatex.validator.batch;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.report.QualityReport;
import com.findatex.validator.validation.Finding;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of a single file in a folder-batch run.
 *
 * <p>For {@link BatchFileStatus#OK} entries, {@code file}, {@code report} and {@code findings}
 * are populated. For {@link BatchFileStatus#LOAD_ERROR} or {@link BatchFileStatus#VALIDATION_ERROR}
 * the {@code errorMessage} carries the failure reason and the data fields are null/empty.
 *
 * <p>Note that retaining the full {@link TptFile} in memory across many results allows
 * subsequent per-file XLSX export but can be memory-intensive for very large batches —
 * see {@link BatchValidationOptions} for trade-offs.
 */
public record BatchResult(
        Path source,
        String displayName,
        BatchFileStatus status,
        TptFile file,
        QualityReport report,
        String errorMessage,
        List<Finding> findings,
        Duration elapsed) {

    public BatchResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(elapsed, "elapsed");
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public static BatchResult ok(Path source, TptFile file, QualityReport report,
                                 List<Finding> findings, Duration elapsed) {
        return new BatchResult(source, fileName(source), BatchFileStatus.OK,
                file, report, null, findings, elapsed);
    }

    public static BatchResult loadError(Path source, String message, Duration elapsed) {
        return new BatchResult(source, fileName(source), BatchFileStatus.LOAD_ERROR,
                null, null, message, List.of(), elapsed);
    }

    public static BatchResult validationError(Path source, TptFile file, String message, Duration elapsed) {
        return new BatchResult(source, fileName(source), BatchFileStatus.VALIDATION_ERROR,
                file, null, message, List.of(), elapsed);
    }

    private static String fileName(Path source) {
        Path name = source.getFileName();
        return name == null ? source.toString() : name.toString();
    }
}
