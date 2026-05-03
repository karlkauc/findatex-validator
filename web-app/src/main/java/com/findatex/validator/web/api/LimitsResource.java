package com.findatex.validator.web.api;

import com.findatex.validator.web.config.WebConfig;
import com.findatex.validator.web.dto.RateLimitStatusDto;
import com.findatex.validator.web.service.RateLimitService;
import com.findatex.validator.web.service.RateLimitStatus;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("/api/limits")
@Produces(MediaType.APPLICATION_JSON)
public class LimitsResource {

    @Inject
    RateLimitService rateLimits;

    @Inject
    WebConfig config;

    @GET
    @Path("/status")
    public RateLimitStatusDto status(@Context HttpServerRequest request) {
        String clientIp = (request != null && request.remoteAddress() != null)
                ? request.remoteAddress().host()
                : null;
        RateLimitStatus s = rateLimits.inspect(clientIp);
        return new RateLimitStatusDto(
                s.limit(),
                s.remaining(),
                s.windowSeconds(),
                s.resetInSeconds(),
                config.desktopDownloadUrl().orElse(null));
    }
}
