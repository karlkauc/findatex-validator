package com.findatex.validator.batch;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.external.ExternalValidationService;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.report.QualityReport;
import com.findatex.validator.report.QualityScorer;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.TemplateRuleSet;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.FindingEnricher;
import com.findatex.validator.validation.ValidationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Sequentially validates a list of files against one shared {@link ValidationEngine},
 * sharing the spec catalog, rule set, scorer and (optional) external-validation client
 * across the whole run.
 *
 * <p>Sequential by design — gives deterministic ordering (so combined reports diff cleanly),
 * monotonic progress reporting, predictable peak memory and stable cancel attribution.
 * Thread-safety of the underlying engine has been verified, so a parallel knob can be
 * added later without breaking this API.
 *
 * <p>External-lookup pooling: a single {@link ExternalValidationService} instance is
 * created once for the run and reused across files. Pooling of GLEIF/OpenFIGI lookups
 * happens through the persistent on-disk cache that the service maintains — no separate
 * pre-aggregation phase is required.
 */
public final class BatchValidationService {

    private static final Logger log = LoggerFactory.getLogger(BatchValidationService.class);

    /** Listener notified as the run progresses. All callbacks are invoked on the worker thread. */
    public interface Listener {
        Listener NOOP = new Listener() {};
        default void onProgress(BatchProgress progress) {}
        default void onFileComplete(BatchResult result) {}
    }

    private final SpecCatalog catalog;
    private final BatchValidationOptions options;

    public BatchValidationService(SpecCatalog catalog, BatchValidationOptions options) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.options = Objects.requireNonNull(options, "options");
    }

    /** Convenience overload — no progress sink, no listener. */
    public BatchSummary run(List<Path> files, BooleanSupplier cancelled) {
        return run(files, cancelled, Listener.NOOP, ExternalValidationService.ProgressSink.NOOP);
    }

    public BatchSummary run(List<Path> files,
                            BooleanSupplier cancelled,
                            Listener listener,
                            ExternalValidationService.ProgressSink externalSink) {
        Objects.requireNonNull(files, "files");
        BooleanSupplier cancel = cancelled == null ? () -> false : cancelled;
        Listener l = listener == null ? Listener.NOOP : listener;
        ExternalValidationService.ProgressSink sink = externalSink == null
                ? ExternalValidationService.ProgressSink.NOOP : externalSink;

        Instant startedAt = Instant.now();
        TptFileLoader loader = new TptFileLoader(catalog);
        TemplateRuleSet ruleSet = options.template().ruleSetFor(options.version());
        com.findatex.validator.template.api.FindingContextSpec contextSpec =
                options.template().findingContextSpec();
        ValidationEngine engine = new ValidationEngine(catalog, ruleSet, contextSpec);
        QualityScorer scorer = new QualityScorer(catalog);

        ExternalValidationConfig externalConfig = options.template()
                .externalValidationConfigFor(options.version());
        boolean externalActive = options.externalValidationEnabled()
                && options.externalCacheDir() != null
                && !externalConfig.isEmpty()
                && options.appSettings().external().enabled();
        ExternalValidationService externalService = externalActive
                ? ExternalValidationService.forProduction(
                        options.externalCacheDir(), options.appSettings().external().isin())
                : null;

        List<BatchResult> results = new ArrayList<>(files.size());
        int total = files.size();
        boolean wasCancelled = false;

        for (int i = 0; i < total; i++) {
            if (cancel.getAsBoolean()) {
                wasCancelled = true;
                break;
            }
            Path file = files.get(i);
            String displayName = file.getFileName() == null ? file.toString() : file.getFileName().toString();
            Instant fileStart = Instant.now();

            l.onProgress(new BatchProgress(i, total, displayName, BatchProgress.Phase.LOADING));
            TptFile tptFile;
            try {
                tptFile = loader.load(file);
            } catch (Exception ex) {
                log.info("Batch: skipping {} — load failed: {}", displayName, ex.getMessage());
                BatchResult br = BatchResult.loadError(file, ex.getMessage(),
                        Duration.between(fileStart, Instant.now()));
                results.add(br);
                l.onFileComplete(br);
                continue;
            }

            try {
                l.onProgress(new BatchProgress(i, total, displayName, BatchProgress.Phase.VALIDATING));
                List<Finding> findings = engine.validate(tptFile, options.activeProfiles());

                if (externalService != null) {
                    l.onProgress(new BatchProgress(i, total, displayName, BatchProgress.Phase.EXTERNAL));
                    List<Finding> online = FindingEnricher.enrich(tptFile,
                            externalService.run(tptFile, externalConfig,
                                    options.appSettings(), cancel, sink),
                            contextSpec);
                    List<Finding> all = new ArrayList<>(findings.size() + online.size());
                    all.addAll(findings);
                    all.addAll(online);
                    findings = all;
                }

                l.onProgress(new BatchProgress(i, total, displayName, BatchProgress.Phase.SCORING));
                QualityReport report = scorer.score(tptFile, options.activeProfiles(), findings);
                BatchResult br = BatchResult.ok(file, tptFile, report, findings,
                        Duration.between(fileStart, Instant.now()));
                results.add(br);
                l.onFileComplete(br);
            } catch (Exception ex) {
                log.warn("Batch: validation failed for {}", displayName, ex);
                BatchResult br = BatchResult.validationError(file, tptFile,
                        ex.getMessage() == null ? ex.toString() : ex.getMessage(),
                        Duration.between(fileStart, Instant.now()));
                results.add(br);
                l.onFileComplete(br);
            }
        }

        Duration totalElapsed = Duration.between(startedAt, Instant.now());
        l.onProgress(new BatchProgress(results.size(), total, "", BatchProgress.Phase.DONE));
        return BatchSummary.of(results,
                options.template().profilesFor(options.version()),
                options.version(),
                options.activeProfiles(),
                startedAt,
                totalElapsed,
                wasCancelled);
    }
}
