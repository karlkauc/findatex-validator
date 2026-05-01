package com.findatex.validator.web.service;

import com.findatex.validator.config.AppSettings;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.external.ExternalValidationService;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.report.QualityReport;
import com.findatex.validator.report.QualityScorer;
import com.findatex.validator.report.ScoreCategory;
import com.findatex.validator.report.XlsxReportWriter;
import com.findatex.validator.spec.SpecCatalog;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
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

    private Semaphore concurrencyGate;

    /** Cache parsed spec catalogs per (template, version) so we don't re-parse XLSX on every request. */
    private final Map<String, CatalogBundle> catalogs = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        concurrencyGate = new Semaphore(Math.max(1, config.maxConcurrency()), true);
        log.info("Validation orchestrator ready (max-concurrency={}, acquire-timeout-ms={})",
                config.maxConcurrency(), config.acquireTimeoutMillis());
    }

    public ValidationResponse validate(String templateId,
                                       String templateVersion,
                                       List<String> profileCodes,
                                       InputStream upload,
                                       String filename,
                                       ExternalOptions externalOptions) {
        boolean acquired;
        try {
            acquired = concurrencyGate.tryAcquire(config.acquireTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebApplicationException("Server busy, try again later.", Response.Status.SERVICE_UNAVAILABLE);
        }
        if (!acquired) {
            throw new WebApplicationException(
                    Response.status(429)
                            .entity("Server busy: too many concurrent validations. Try again shortly.")
                            .header("Retry-After", "5")
                            .build());
        }
        try {
            return doValidate(templateId, templateVersion, profileCodes, upload, filename, externalOptions);
        } finally {
            concurrencyGate.release();
        }
    }

    private ValidationResponse doValidate(String templateId,
                                          String templateVersion,
                                          List<String> profileCodes,
                                          InputStream upload,
                                          String filename,
                                          ExternalOptions externalOptions) {
        TemplateDefinition def = resolveTemplate(templateId);
        TemplateVersion version = resolveVersion(def, templateVersion);
        ProfileSet profileSet = def.profilesFor(version);
        Set<ProfileKey> activeProfiles = resolveProfiles(profileSet, profileCodes);

        CatalogBundle bundle = catalogs.computeIfAbsent(def.id() + "/" + version.version(), k -> {
            SpecCatalog catalog = def.specLoaderFor(version).load();
            TemplateRuleSet ruleSet = def.ruleSetFor(version);
            return new CatalogBundle(catalog, ruleSet, profileSet);
        });

        TptFile file;
        try {
            file = new TptFileLoader(bundle.catalog).load(upload, filename);
        } catch (IOException e) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Could not parse upload: " + e.getMessage())
                            .build());
        }

        // Pre-flight: zero mapped fields with non-empty headers means the file
        // doesn't match the chosen template/version. Without this, every row ×
        // every mandatory field becomes a "missing" finding — easily 100k+ findings
        // on a multi-thousand-row file, which exhausts the JVM heap.
        if (file.headerToNumKey().isEmpty() && !file.rawHeaders().isEmpty()) {
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
                    List<Finding> online = FindingEnricher.enrich(file,
                            svc.run(file, externalCfg, settings, () -> false,
                                    ExternalValidationService.ProgressSink.NOOP),
                            def.findingContextSpec());
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

        Path xlsxPath;
        try {
            xlsxPath = Files.createTempFile("findatex-report-", ".xlsx");
            new XlsxReportWriter(bundle.catalog,
                    bundle.profileSet,
                    version,
                    com.findatex.validator.report.GenerationUi.WEB)
                    .write(report, xlsxPath);
        } catch (IOException e) {
            throw new WebApplicationException(
                    Response.serverError().entity("Could not write report: " + e.getMessage()).build());
        }
        UUID reportId = reportStore.store(xlsxPath);

        return assembleResponse(def, version, file, report, findings, reportId);
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
                                                UUID reportId) {
        long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        long warnings = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
        long infos = findings.stream().filter(f -> f.severity() == Severity.INFO).count();

        ValidationResponse.Summary summary = new ValidationResponse.Summary(
                def.id().name(),
                version.version(),
                sanitizeFilename(file.source().getFileName().toString()),
                file.rows().size(),
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

        return new ValidationResponse(summary, scores, perProfile, perFund, findingDtos, reportId.toString());
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

    private record CatalogBundle(SpecCatalog catalog, TemplateRuleSet ruleSet, ProfileSet profileSet) {
    }
}
