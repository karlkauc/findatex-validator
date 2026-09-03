package com.findatex.validator.web.api;

import com.findatex.validator.web.service.ReportStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.UUID;

/**
 * Serves the gzip-JSON annotated-source side artifact of a validation run (see
 * {@code AnnotatedSourceJson} for the document shape). The id is the run's
 * {@code reportId}. Read-many within the report TTL — unlike the XLSX download it is
 * not single-use, and fetching it does not consume the report.
 *
 * <p>The file on disk already is the gzip wire form, so it is sent verbatim with
 * {@code Content-Encoding: gzip}; browsers and RestAssured decode transparently.
 * Deliberately no rate limit and no usage-stats event: the run itself was already
 * counted and the view is part of the same result page.</p>
 */
@Path("/api/annotated-source")
public class AnnotatedSourceResource {

    @Inject
    ReportStore reportStore;

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException();
        }
        java.nio.file.Path path = reportStore.annotatedSource(uuid)
                .orElseThrow(NotFoundException::new);

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            // Expired between lookup and read — same answer as never having existed.
            throw new NotFoundException();
        } catch (IOException e) {
            throw new NotFoundException();
        }

        return Response.ok(bytes)
                .type(MediaType.APPLICATION_JSON)
                .header("Content-Encoding", "gzip")
                .header("Cache-Control", "private, no-store")
                .build();
    }
}
