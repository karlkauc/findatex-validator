package com.findatex.validator.web.api;

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
            @RestForm("file") FileUpload file) {

        if (file == null || file.uploadedFile() == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("'file' multipart part is required").build());
        }

        String filename = file.fileName();
        if (filename == null || filename.isBlank()) filename = "uploaded.xlsx";

        try (InputStream in = Files.newInputStream(file.uploadedFile())) {
            return orchestrator.validate(templateId, templateVersion, profiles, in, filename);
        } catch (IOException e) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Could not read upload: " + e.getMessage()).build());
        }
    }
}
