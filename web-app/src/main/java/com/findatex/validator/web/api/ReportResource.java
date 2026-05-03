package com.findatex.validator.web.api;

import com.findatex.validator.web.service.ReportStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

@Path("/api/report")
public class ReportResource {

    @Inject
    ReportStore reportStore;

    @GET
    @Path("/{id}")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response download(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException();
        }

        // Atomic take: removes the entry from the cache and returns its path
        // in one operation. Closes the TOCTOU race that an earlier "get + stream
        // + invalidate-in-finally" pattern allowed (two concurrent GETs could
        // both observe the entry and both stream it). The caller owns the file
        // from this point — we delete it once the response body is fully written.
        java.nio.file.Path path = reportStore.take(uuid)
                .orElseThrow(NotFoundException::new);

        StreamingOutput stream = out -> {
            try (InputStream in = Files.newInputStream(path)) {
                in.transferTo(out);
            } finally {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ignored) {
                    // best-effort: TTL listener is not in play here (EXPLICIT cause was
                    // skipped on take); leftover tempfiles will be cleaned by the OS tmp reaper.
                }
            }
        };

        String filename = "findatex-report-" + uuid + ".xlsx";
        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }
}
