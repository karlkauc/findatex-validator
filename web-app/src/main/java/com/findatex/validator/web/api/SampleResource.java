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
 * Serves the per-template, per-version demo files behind "Try an example".
 * {@code GET /api/samples/{templateId}/{version}} is what {@code GET /api/templates}
 * advertises; {@code GET /api/samples/{templateId}} (the pre-V8 URL) keeps working
 * and returns the template's first sample.
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
        SampleFiles.Sample sample = SampleFiles.forTemplate(templateId).stream()
                .findFirst()
                .orElseThrow(NotFoundException::new);
        return serve(sample);
    }

    @GET
    @jakarta.ws.rs.Path("/{templateId}/{version}")
    @Produces(XLSX)
    public Response download(@PathParam("templateId") String templateId,
                             @PathParam("version") String version) {
        return serve(SampleFiles.forTemplate(templateId, version)
                .orElseThrow(NotFoundException::new));
    }

    private Response serve(SampleFiles.Sample sample) {
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
