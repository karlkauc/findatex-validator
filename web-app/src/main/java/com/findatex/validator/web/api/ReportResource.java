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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

        java.nio.file.Path path = reportStore.get(uuid)
                .orElseThrow(NotFoundException::new);

        StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try (InputStream in = Files.newInputStream(path)) {
                    in.transferTo(out);
                } finally {
                    // One-shot download: invalidate immediately so the cache eviction listener
                    // deletes the temp file from disk. Subsequent GETs for this id return 404.
                    reportStore.invalidate(uuid);
                }
            }
        };

        String filename = "findatex-report-" + uuid + ".xlsx";
        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }
}
