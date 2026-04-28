package com.findatex.validator.web.api;

import com.findatex.validator.web.dto.ExternalOptions;
import com.findatex.validator.web.dto.ValidationResponse;
import com.findatex.validator.web.service.ValidationOrchestrator;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
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

        validateMagicBytes(file.uploadedFile(), filename);

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
            return orchestrator.validate(templateId, templateVersion, profiles, in, filename, opts);
        } catch (IOException e) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Could not read upload: " + e.getMessage()).build());
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
