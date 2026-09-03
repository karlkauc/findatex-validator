package com.findatex.validator.web.api;

import com.findatex.validator.web.service.ClientContextFactory;
import com.findatex.validator.web.service.SampleFiles;
import com.findatex.validator.web.service.UsageStatsService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Serves the per-template demo file behind "Try a sample file".
 *
 * <p>Under {@code /api/} on purpose: {@code robots.txt} disallows that prefix,
 * so the fixtures do not end up indexed as if they were real reference data.
 * They are small (a few kB) and read straight from the classpath, so they are
 * streamed without caching machinery.
 */
@Path("/api/samples")
public class SampleResource {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Inject
    UsageStatsService usageStats;

    @Inject
    ClientContextFactory clientContexts;

    @Context
    HttpServerRequest request;

    @GET
    @jakarta.ws.rs.Path("/{templateId}")
    @Produces(XLSX)
    public Response download(@PathParam("templateId") String templateId) {
        SampleFiles.Sample sample = SampleFiles.forTemplate(templateId)
                .orElseThrow(NotFoundException::new);
        Optional<InputStream> stream = sample.open();
        if (stream.isEmpty()) throw new NotFoundException();
        try (InputStream in = stream.get()) {
            recordSampleLoad(sample);
            return Response.ok(in.readAllBytes(), XLSX)
                    .header("Content-Disposition",
                            "attachment; filename=\"" + sample.filename() + "\"")
                    .header("Cache-Control", "public, max-age=3600")
                    .build();
        } catch (IOException e) {
            throw new NotFoundException();
        }
    }

    /** "Try an example" clicked — separates playing from real usage in the stats. Best-effort. */
    private void recordSampleLoad(SampleFiles.Sample sample) {
        try {
            usageStats.recordSampleLoad(sample.templateId(), sample.version(),
                    clientContexts.from(request));
        } catch (RuntimeException e) {
            // stats must never affect the download
        }
    }
}
