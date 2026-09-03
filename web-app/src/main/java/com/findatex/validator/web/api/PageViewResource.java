package com.findatex.validator.web.api;

import com.findatex.validator.web.dto.PageViewDto;
import com.findatex.validator.web.service.BotDetector;
import com.findatex.validator.web.service.ClientContextFactory;
import com.findatex.validator.web.service.PageViewService;
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

/**
 * Beacon endpoint for page views, fired once per page load by the SPA.
 *
 * <p>Contract: <b>always 204</b>, whatever happens — feature off, bot, garbage
 * body, dead database. A counter must never make the page look broken, and a
 * differentiated answer would only tell a probing client whether the feature is
 * on. Persistence is asynchronous, so a 204 does not prove the row was written.
 *
 * <p>Views from automated clients are dropped here rather than filtered later
 * ({@link BotDetector}) — a bot row costs nothing to store but silently
 * inflates the one number this exists to produce. Country, daily visitor hash
 * and device class come from {@link ClientContextFactory}; the raw IP is never
 * persisted or logged.
 *
 * <p>Per-IP rate limiting is enforced by {@code RateLimitFilter}.
 */
@Path("/api/page-view")
public class PageViewResource {

    private static final Logger log = LoggerFactory.getLogger(PageViewResource.class);

    @Inject
    PageViewService pageViews;

    @Inject
    ClientContextFactory clientContexts;

    @Context
    HttpServerRequest request;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response record(@HeaderParam("User-Agent") String userAgent, PageViewDto dto) {
        if (!pageViews.enabled() || dto == null) return noContent();
        if (BotDetector.isBot(userAgent)) return noContent();

        try {
            pageViews.record(dto.path(), dto.referrer(), dto.campaign(), clientContexts.from(request));
        } catch (RuntimeException e) {
            log.debug("Page-view: recording failed (ignored): {}", e.toString());
        }
        return noContent();
    }

    private static Response noContent() {
        return Response.noContent().build();
    }
}
