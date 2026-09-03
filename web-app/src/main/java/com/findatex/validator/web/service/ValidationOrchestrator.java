package com.findatex.validator.web.service;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.external.ExternalValidationService;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.report.AnnotatedSourceJson;
import com.findatex.validator.report.AnnotatedSourceModel;
import com.findatex.validator.report.QualityReport;
import com.findatex.validator.report.QualityScorer;
import com.findatex.validator.report.ScoreCategory;
import com.findatex.validator.report.XlsxReportWriter;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.stats.ExternalLookupCounter;
import com.findatex.validator.stats.FileNameShape;
import com.findatex.validator.stats.UsageEvent;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.template.api.TemplateRuleSet;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.FindingEnricher;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationEngine;
import com.findatex.validator.web.config.WebConfig;
import com.findatex.validator.web.dto.ExternalOptions;
import com.findatex.validator.web.dto.FindingDto;
import com.findatex.validator.web.dto.PerFundScoreDto;
import com.findatex.validator.web.dto.ScoreDto;
import com.findatex.validator.web.dto.ValidationResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Wraps the validation core for HTTP callers. Enforces a global concurrency cap (so a
 * burst of uploads can't OOM the server), produces an in-memory {@link ValidationResponse}
 * for the JSON path, and writes the XLSX report to a tempfile that is registered with
 * {@link ReportStore} for one-shot download.
 */
@ApplicationScoped
public class ValidationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ValidationOrchestrator.class);

    @Inject
    WebConfig config;

    @Inject
    ReportStore reportStore;

    @Inject
    ExternalValidationFactory externalFactory;

    @Inject
    UsageStatsService usageStatsService;

    private Semaphore concurrencyGate;

    /** Cache parsed spec catalogs per (template, version) so we don't re-parse XLSX on every request. */
    private final Map<String, CatalogBundle> catalogs = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        concurrencyGate = new Semaphore(Math.max(1, config.maxConcurrency()), true);
        log.info("Validation orchestrator ready (max-concurrency={}, acquire-timeout-ms={})",
                config.maxConcurrency(), config.acquireTimeoutMillis());
    }

    /**
     * @param filename   client-supplied upload name — used for format dispatch
     *                   and reduced to non-identifying classes for the stats
     *                   ({@link FileNameShape}); never stored
     * @param inputBytes upload size, or -1 when unknown
     * @param ctx        request-derived client context for the usage stats
     */
    public ValidationResponse validate(String templateId,
                                       String templateVersion,
                                       List<String> profileCodes,
                                       InputStream upload,
                                       String filename,
                                       long inputBytes,
                                       ExternalOptions externalOptions,
                                       ClientContext ctx) {
        UsageEvent.Input input = FileNameShape.of(filename, inputBytes < 0 ? null : inputBytes);
        boolean isSample = SampleFiles.isSampleFilename(filename);
        boolean acquired;
        try {
            acquired = concurrencyGate.tryAcquire(config.acquireTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordFailure(UsageEvent.STATUS_BUSY, templateId, templateVersion, input, isSample, null, ctx);
            throw new WebApplicationException("Server busy, try again later.", Response.Status.SERVICE_UNAVAILABLE);
        }
        if (!acquired) {
            recordFailure(UsageEvent.STATUS_BUSY, templateId, templateVersion, input, isSample, null, ctx);
            throw new WebApplicationException(
                    Response.status(429)
                            .entity("Server busy: too many concurrent validations. Try again shortly.")
                            .header("Retry-After", "5")
                            .build());
        }
        try {
            return doValidate(templateId, templateVersion, profileCodes, upload,
                    filename, input, isSample, externalOptions, ctx);
        } finally {
            concurrencyGate.release();
        }
    }

    private ValidationResponse doValidate(String templateId,
                                          String templateVersion,
                                          List<String> profileCodes,
                                          InputStream upload,
                                          String filename,
                                          UsageEvent.Input input,
                                          boolean isSample,
                                          ExternalOptions externalOptions,
                                          ClientContext ctx) {
        long t0 = System.nanoTime();
        TemplateDefinition def;
        TemplateVersion version;
        Set<ProfileKey> activeProfiles;
        ProfileSet profileSet;
        try {
            def = resolveTemplate(templateId);
            version = resolveVersion(def, templateVersion);
            profileSet = def.profilesFor(version);
            activeProfiles = resolveProfiles(profileSet, profileCodes);
        } catch (WebApplicationException e) {
            recordFailure(UsageEvent.STATUS_BAD_REQUEST, templateId, templateVersion, input, isSample,
                    elapsedMs(t0), ctx);
            throw e;
        }
        String templateName = def.id().name();
        String versionName = version.version();

        CatalogBundle bundle = catalogs.computeIfAbsent(def.id() + "/" + version.version(), k -> {
            SpecCatalog catalog = def.specLoaderFor(version).load();
            TemplateRuleSet ruleSet = def.ruleSetFor(version);
            return new CatalogBundle(catalog, ruleSet, profileSet);
        });

        TptFile file;
        try {
            file = new TptFileLoader(bundle.catalog).load(upload, filename);
        } catch (IOException e) {
            // Don't echo POI's exception text to the client — it can leak internal
            // class names and host paths. Full detail is logged server-side.
            log.warn("Upload parse failed for filename '{}': {}", filename, e.toString());
            recordFailure(UsageEvent.STATUS_PARSE_ERROR, templateName, versionName, input, isSample,
                    elapsedMs(t0), ctx);
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Could not parse upload. Check that the file is a valid XLSX/CSV "
                                    + "matching the chosen template.")
                            .build());
        }

        // Pre-flight: zero mapped fields with non-empty headers means the file
        // doesn't match the chosen template/version. Without this, every row ×
        // every mandatory field becomes a "missing" finding — easily 100k+ findings
        // on a multi-thousand-row file, which exhausts the JVM heap.
        if (file.headerToNumKey().isEmpty() && !file.rawHeaders().isEmpty()) {
            recordFailure(UsageEvent.STATUS_TEMPLATE_MISMATCH, templateName, versionName, input, isSample,
                    elapsedMs(t0), ctx);
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("File does not match template " + def.id() + " " + version.version()
                                    + ": none of the " + file.rawHeaders().size()
                                    + " column header(s) are recognized. "
                                    + "Check that you picked the right template and version.")
                            .build());
        }

        // Honour the file-level "Reporting" Y/N flags (e.g. EET fields 6–10): if the producer
        // marked SFDR_ENTITY = 'N' we suppress the SFDR_ENTITY rules so they don't fire on
        // every row. Each suppressed profile gets one INFO finding so the user sees the gate
        // took effect rather than wondering why some fields aren't being checked.
        Set<ProfileKey> gatedProfiles = def.activeProfilesForFile(version, file, activeProfiles);
        List<Finding> profileGateNotes = new ArrayList<>();
        for (ProfileKey p : activeProfiles) {
            if (!gatedProfiles.contains(p)) {
                profileGateNotes.add(Finding.info(
                        "PROFILE/" + p.code() + "/SKIPPED-PER-FILE-FLAG",
                        p, null, null, null, null,
                        "Profile " + p.displayName()
                                + " not active in this file (Data Reporting flag is not 'Y') — rules skipped."));
            }
        }

        List<Finding> findings = new ValidationEngine(bundle.catalog, bundle.ruleSet, def.findingContextSpec())
                .validate(file, gatedProfiles);
        if (!profileGateNotes.isEmpty()) {
            List<Finding> merged = new ArrayList<>(findings.size() + profileGateNotes.size());
            merged.addAll(profileGateNotes);
            merged.addAll(findings);
            findings = merged;
        }

        ExternalValidationConfig externalCfg = def.externalValidationConfigFor(version);
        UsageEvent.External external = null;
        if (externalOptions != null
                && externalOptions.enabled()
                && !externalCfg.isEmpty()
                && config.external().enabled()
                && externalFactory.enabled()) {
            try (ExternalValidationFactory.ServiceHandle handle =
                         externalFactory.resolve(externalOptions.userOpenfigiKey())) {
                ExternalValidationService svc = handle.service();
                if (svc != null) {
                    AppSettings settings = externalFactory.buildSettings(externalOptions);
                    // Count the online phase for the usage stats (lookups, cache
                    // hits, duration, unavailable phases) — counts only, never keys.
                    ExternalLookupCounter counter = new ExternalLookupCounter(
                            ExternalValidationService.ProgressSink.NOOP);
                    long e0 = System.nanoTime();
                    List<Finding> online = FindingEnricher.enrich(file,
                            svc.run(file, externalCfg, settings, () -> false, counter),
                            def.findingContextSpec(), bundle.catalog);
                    external = counter.finish(elapsedMs(e0), online);
                    List<Finding> merged = new ArrayList<>(findings);
                    merged.addAll(online);
                    findings = merged;
                }
            } catch (RuntimeException e) {
                // The service swallows API failures internally and emits EXTERNAL/...UNAVAILABLE
                // info findings. Anything reaching here is an unexpected programming error;
                // keep the local findings and continue. Never log the user key.
                log.warn("External validation phase aborted unexpectedly: {}", e.getMessage());
            }
        }

        QualityReport report = new QualityScorer(bundle.catalog).score(file, gatedProfiles, findings);

        // The annotated-source model is shared by the XLSX sheet and the JSON side artifact
        // for the SPA, so it is built once here (or not at all when the grid is over the cap
        // — the XLSX writer then builds its own for the sheet). Best-effort: a failure here
        // degrades to "no in-browser view", never to a failed validation.
        AnnotatedSourceModel annotatedModel = buildAnnotatedModel(file, report);

        Path xlsxPath;
        try {
            xlsxPath = createOwnerOnlyTempFile("findatex-report-", ".xlsx");
            new XlsxReportWriter(bundle.catalog,
                    bundle.profileSet,
                    version,
                    com.findatex.validator.report.GenerationUi.WEB)
                    .write(report, xlsxPath, annotatedModel);
        } catch (IOException e) {
            log.error("Could not write report tempfile", e);
            throw new WebApplicationException(
                    Response.serverError().entity("Could not write report.").build());
        }
        // The JSON's findingCells index into report.findings() — the very list
        // assembleResponse() maps 1:1 into ValidationResponse.findings (see there).
        Path annotatedPath = annotatedModel == null
                ? null : writeAnnotatedSource(annotatedModel, report.findings());
        UUID reportId = reportStore.store(xlsxPath, annotatedPath, templateName, versionName);

        try {
            boolean ext = externalOptions != null && externalOptions.enabled()
                    && config.external().enabled();
            usageStatsService.record(
                    UsageEvent.forWeb(report, def, version, ext, elapsedMs(t0), input, external, isSample),
                    ctx);
        } catch (RuntimeException e) {
            // Stats are best-effort and must never affect the validation response.
            log.debug("Usage-stats recording skipped: {}", e.toString());
        }

        return assembleResponse(def, version, file, report, findings, reportId, annotatedPath != null);
    }

    /**
     * Builds the annotated-source join when the upload is within the configured cap;
     * {@code null} when it is over the cap or the source could not be re-read.
     */
    private AnnotatedSourceModel buildAnnotatedModel(TptFile file, QualityReport report) {
        WebConfig.AnnotatedSource caps = config.annotatedSource();
        int rows = file.rows().size();
        int width = file.rawHeaders().size();
        if (!AnnotatedSourceJson.withinLimits(rows, width, caps.maxRows(), caps.maxCells())) {
            log.debug("Annotated source skipped: {} rows x {} cols exceeds cap ({} rows / {} cells)",
                    rows, width, caps.maxRows(), caps.maxCells());
            return null;
        }
        try {
            return AnnotatedSourceModel.build(report);
        } catch (IOException | RuntimeException e) {
            log.warn("Annotated source unavailable for this run: {}", e.toString());
            return null;
        }
    }

    /**
     * Writes the gzip-JSON side artifact; {@code null} (and no leftover file) on any failure.
     * The validation result is never failed over this side artifact.
     */
    private static Path writeAnnotatedSource(AnnotatedSourceModel model, List<Finding> findings) {
        Path path = null;
        try {
            path = createOwnerOnlyTempFile("findatex-annotated-", ".json.gz");
            try (java.io.OutputStream out = Files.newOutputStream(path)) {
                AnnotatedSourceJson.write(model, findings, out);
            }
            return path;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not write annotated-source tempfile: {}", e.toString());
            if (path != null) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort; the OS tmp reaper picks up the rest
                }
            }
            return null;
        }
    }

    private static long elapsedMs(long t0) {
        return java.time.Duration.ofNanos(System.nanoTime() - t0).toMillis();
    }

    /** A validation attempt that produced no report is still an event — only its class is kept. */
    private void recordFailure(String status, String templateId, String templateVersion,
                               UsageEvent.Input input, boolean isSample, Long durationMs,
                               ClientContext ctx) {
        try {
            usageStatsService.recordFailedRun(status,
                    blankToNull(templateId), blankToNull(templateVersion), input, isSample,
                    durationMs == null ? null : (int) Math.min(durationMs, Integer.MAX_VALUE), ctx);
        } catch (RuntimeException e) {
            // Stats are best-effort and must never affect the validation response.
            log.debug("Usage-stats failure recording skipped: {}", e.toString());
        }
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t.length() > 40 ? t.substring(0, 40) : t;
    }

    private static TemplateDefinition resolveTemplate(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            throw badRequest("templateId is required (one of TPT, EET, EMT, EPT)");
        }
        try {
            return TemplateRegistry.of(com.findatex.validator.template.api.TemplateId.valueOf(
                    templateId.trim().toUpperCase()));
        } catch (IllegalArgumentException | java.util.NoSuchElementException e) {
            throw badRequest("Unknown templateId: " + templateId);
        }
    }

    private static TemplateVersion resolveVersion(TemplateDefinition def, String requested) {
        if (requested == null || requested.isBlank()) return def.latest();
        for (TemplateVersion v : def.versions()) {
            if (v.version().equalsIgnoreCase(requested)) return v;
        }
        throw badRequest("Unknown version '" + requested + "' for template " + def.id()
                + " (known: " + def.versions().stream().map(TemplateVersion::version).toList() + ")");
    }

    private static Set<ProfileKey> resolveProfiles(ProfileSet profileSet, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            // Empty list means "all available profiles" — same default the JavaFX UI uses.
            return new java.util.LinkedHashSet<>(profileSet.all());
        }
        Set<ProfileKey> active = new java.util.LinkedHashSet<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) continue;
            ProfileKey key = profileSet.byCode(code.trim()).orElseThrow(() ->
                    badRequest("Unknown profile code: " + code));
            active.add(key);
        }
        return active;
    }

    private ValidationResponse assembleResponse(TemplateDefinition def,
                                                TemplateVersion version,
                                                TptFile file,
                                                QualityReport report,
                                                List<Finding> findings,
                                                UUID reportId,
                                                boolean annotatedSourceAvailable) {
        long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        long warnings = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
        long infos = findings.stream().filter(f -> f.severity() == Severity.INFO).count();

        ValidationResponse.Summary summary = new ValidationResponse.Summary(
                def.id().name(),
                version.version(),
                sanitizeFilename(file.source().getFileName().toString()),
                file.rows().size(),
                report.cleanRowCount(),
                findings.size(),
                (int) errors,
                (int) warnings,
                (int) infos,
                Instant.now().toString()
        );

        List<ScoreDto> scores = new ArrayList<>();
        for (Map.Entry<ScoreCategory, Double> e : report.scores().entrySet()) {
            scores.add(ScoreDto.of(e.getKey().name(), e.getValue()));
        }

        Map<String, List<ScoreDto>> perProfile = new LinkedHashMap<>();
        for (var pe : report.perProfileScores().entrySet()) {
            List<ScoreDto> list = pe.getValue().entrySet().stream()
                    .map(en -> ScoreDto.of(en.getKey().name(), en.getValue()))
                    .collect(Collectors.toList());
            perProfile.put(pe.getKey().code(), list);
        }

        // INVARIANT: findingDtos[i] corresponds to findings[i] == report.findings().get(i)
        // (same list, same order, no filtering). The annotated-source JSON's findingCells
        // carry indices into report.findings(), and the SPA joins them against
        // ValidationResponse.findings by position — AnnotatedSourceLifecycleTest checks it.
        List<FindingDto> findingDtos = findings.stream().map(FindingDto::from).toList();

        List<PerFundScoreDto> perFund = new ArrayList<>();
        List<com.findatex.validator.domain.FundGroup> fundGroups =
                com.findatex.validator.domain.FundGrouper.group(file);
        for (com.findatex.validator.domain.FundGroup g : fundGroups) {
            Map<ScoreCategory, Double> sc = report.perFundScores().get(g.key());
            if (sc == null) continue;
            List<ScoreDto> dtos = new ArrayList<>();
            for (Map.Entry<ScoreCategory, Double> e : sc.entrySet()) {
                dtos.add(ScoreDto.of(e.getKey().name(), e.getValue()));
            }
            // Field 3 = TPT portfolio name. Non-TPT templates yield an empty
            // perFundScores map, so this loop is a no-op there.
            String name = null;
            for (com.findatex.validator.domain.TptRow r : g.rows()) {
                String v = r.stringValue("3").orElse(null);
                if (v != null && !v.isEmpty()) { name = v; break; }
            }
            perFund.add(new PerFundScoreDto(
                    g.key().portfolioId(),
                    name,
                    g.key().valuationDate(),
                    dtos));
        }

        return new ValidationResponse(summary, scores, perProfile, perFund, findingDtos,
                reportId.toString(), annotatedSourceAvailable);
    }

    private static WebApplicationException badRequest(String message) {
        return new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST).entity(message).build());
    }

    /**
     * Strips anything that isn't a word char, dot or dash from the user-supplied
     * filename before echoing it in the JSON response. Defends against
     * client-side mishandling that might render the filename as HTML.
     */
    private static String sanitizeFilename(String s) {
        if (s == null || s.isBlank()) return "uploaded";
        String cleaned = s.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    @PreDestroy
    void shutdown() {
        catalogs.clear();
    }

    /**
     * Creates a tempfile readable + writable only by the running process owner.
     * On POSIX systems we explicitly set 0600 so a co-tenant under the same
     * UID (and the broader umask-default 0644 case on shared hosts) cannot read
     * the report during the 5-minute single-use TTL window. On Windows the
     * default ACL is already user-private, so we fall back to the standard call.
     */
    private static Path createOwnerOnlyTempFile(String prefix, String suffix) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            FileAttribute<?> ownerOnly = PosixFilePermissions.asFileAttribute(
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            return Files.createTempFile(prefix, suffix, ownerOnly);
        }
        return Files.createTempFile(prefix, suffix);
    }

    private record CatalogBundle(SpecCatalog catalog, TemplateRuleSet ruleSet, ProfileSet profileSet) {
    }
}
