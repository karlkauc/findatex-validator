package com.findatex.validator.web.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.findatex.validator.web.config.WebConfig;
import com.findatex.validator.web.dto.UsageStatsDto;
import com.findatex.validator.web.service.GeoIpService;
import com.findatex.validator.web.service.UsageStatsService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Ingest endpoint for anonymous usage events posted by the desktop app.
 *
 * <p>Contract: returns <b>202</b> immediately and persists asynchronously. A
 * configured ingest token is required (constant-time compared); when no token
 * is configured the feature is off and the endpoint accepts-and-discards (202).
 * Malformed JSON yields 202 (logged at DEBUG) — never a 5xx for data errors, so
 * a buggy client can never be disturbed by this endpoint. 413 (body size) and
 * 429 (per-IP rate limit) are enforced by existing infrastructure.
 *
 * <p>{@code country_code} is derived here from the TCP source IP; the raw IP is
 * never persisted or logged.
 */
@Path("/api/usage-stats")
public class UsageStatsResource {

    private static final Logger log = LoggerFactory.getLogger(UsageStatsResource.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    WebConfig config;

    @Inject
    UsageStatsService usageStats;

    @Inject
    GeoIpService geoIp;

    @Context
    HttpServerRequest request;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ingest(@HeaderParam("X-Usage-Token") String token, String body) {
        var configured = config.usageStats().ingestToken();
        if (configured.isEmpty()) {
            // Feature off: accept so clients don't retry/notice, but store nothing.
            return Response.accepted().build();
        }
        if (!constantTimeEquals(configured.get(), token)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        UsageStatsDto dto;
        try {
            dto = MAPPER.readValue(body, UsageStatsDto.class);
        } catch (Exception e) {
            // Bad payload — swallow, never 5xx out to the client.
            log.debug("Usage-stats: discarding unparseable body ({})", e.getClass().getSimpleName());
            return Response.accepted().build();
        }
        if (dto == null) return Response.accepted().build();

        String country = null;
        try {
            country = geoIp.countryFor(clientIp());
        } catch (RuntimeException e) {
            log.debug("Usage-stats: geo lookup failed (ignored)");
        }
        usageStats.record(dto, "desktop", country);
        return Response.accepted().build();
    }

    private String clientIp() {
        if (request == null || request.remoteAddress() == null) return null;
        return request.remoteAddress().host();
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        if (provided == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
