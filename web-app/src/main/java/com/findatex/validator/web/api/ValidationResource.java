package com.findatex.validator.web.api;

import com.findatex.validator.stats.FileNameShape;
import com.findatex.validator.stats.UsageEvent;
import com.findatex.validator.web.dto.ExternalOptions;
import com.findatex.validator.web.dto.ValidationResponse;
import com.findatex.validator.web.service.ClientContext;
import com.findatex.validator.web.service.ClientContextFactory;
import com.findatex.validator.web.service.SampleFiles;
import com.findatex.validator.web.service.UsageStatsService;
import com.findatex.validator.web.service.ValidationOrchestrator;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

@Path("/api/validate")
public class ValidationResource {

    @Inject
    ValidationOrchestrator orchestrator;

    @Inject
    ClientContextFactory clientContexts;

    @Inject
    UsageStatsService usageStats;

    @Context
    HttpServerRequest request;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public ValidationResponse validate(
            @RestForm("templateId") String templateId,
            @RestForm("templateVersion") String templateVersion,
            @RestForm("profiles") List<String> profiles,
            @RestForm("file") FileUpload file,
            @RestForm("externalEnabled") String externalEnabled,
            @RestForm("leiEnabled") String leiEnabled,
            @RestForm("leiCheckLapsed") String leiCheckLapsed,
            @RestForm("leiCheckName") String leiCheckName,
            @RestForm("leiCheckCountry") String leiCheckCountry,
            @RestForm("isinEnabled") String isinEnabled,
            @RestForm("isinCheckCurrency") String isinCheckCurrency,
            @RestForm("isinCheckCic") String isinCheckCic,
            @RestForm("openfigiApiKey") String openfigiApiKey) {

        if (file == null || file.uploadedFile() == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("'file' multipart part is required").build());
        }

        String filename = file.fileName();
        if (filename == null || filename.isBlank()) filename = "uploaded.xlsx";

        ClientContext ctx = clientContexts.from(request);
        long size = uploadSize(file);

        try {
            validateMagicBytes(file.uploadedFile(), filename);
        } catch (WebApplicationException e) {
            // Rejected before parsing: still a validation attempt for the stats.
            String status = FileNameShape.format(filename) == null
                    ? UsageEvent.STATUS_UNSUPPORTED_TYPE : UsageEvent.STATUS_PARSE_ERROR;
            recordRejected(status, templateId, templateVersion, filename, size, ctx);
            throw e;
        }

        ExternalOptions opts = new ExternalOptions(
                parseBool(externalEnabled, false),
                parseBool(leiEnabled, true),
                parseBool(leiCheckLapsed, true),
                parseBool(leiCheckName, false),
                parseBool(leiCheckCountry, false),
                parseBool(isinEnabled, true),
                parseBool(isinCheckCurrency, false),
                parseBool(isinCheckCic, false),
                Optional.ofNullable(openfigiApiKey)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty()));

        try (InputStream in = Files.newInputStream(file.uploadedFile())) {
            return orchestrator.validate(templateId, templateVersion, profiles, in,
                    filename, size, opts, ctx);
        } catch (IOException e) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Could not read upload: " + e.getMessage()).build());
        }
    }

    private static long uploadSize(FileUpload file) {
        try {
            long s = file.size();
            if (s > 0) return s;
            return Files.size(file.uploadedFile());
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    private void recordRejected(String status, String templateId, String templateVersion,
                                String filename, long size, ClientContext ctx) {
        try {
            usageStats.recordFailedRun(status, templateId, templateVersion,
                    FileNameShape.of(filename, size < 0 ? null : size),
                    SampleFiles.isSampleFilename(filename), null, ctx);
        } catch (RuntimeException e) {
            // stats must never affect the response
        }
    }

    private static boolean parseBool(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return Boolean.parseBoolean(raw.trim());
    }

    /**
     * Cheap content sniffing on top of the user-supplied filename: makes sure that an
     * .xlsx upload actually starts with the ZIP magic bytes and that a .csv upload
     * doesn't contain NUL bytes (a strong signal it's binary). Lets POI / CommonsCSV
     * handle the deeper validation but rejects obvious mismatches up front so they
     * don't land as opaque parser errors deep in the orchestrator.
     */
    private static void validateMagicBytes(java.nio.file.Path path, String filename) {
        String lower = filename.toLowerCase();
        boolean isXlsx = lower.endsWith(".xlsx") || lower.endsWith(".xlsm");
        boolean isCsv = lower.endsWith(".csv") || lower.endsWith(".txt");
        if (!isXlsx && !isCsv) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Unsupported file type. Expected .xlsx or .csv").build());
        }
        try (InputStream in = Files.newInputStream(path)) {
            byte[] head = in.readNBytes(4096);
            if (isXlsx) {
                if (head.length < 4 || head[0] != 'P' || head[1] != 'K' || head[2] != 0x03 || head[3] != 0x04) {
                    throw new WebApplicationException(
                            Response.status(Response.Status.BAD_REQUEST)
                                    .entity("File is not a valid XLSX (missing ZIP signature).").build());
                }
            } else { // CSV / TXT
                for (byte b : head) {
                    if (b == 0) {
                        throw new WebApplicationException(
                                Response.status(Response.Status.BAD_REQUEST)
                                        .entity("File looks binary; CSV must be plain text.").build());
                    }
                }
            }
        } catch (IOException e) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Could not read upload header: " + e.getMessage()).build());
        }
    }
}
