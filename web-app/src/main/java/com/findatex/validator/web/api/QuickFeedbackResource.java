package com.findatex.validator.web.api;

import com.findatex.validator.quickfeedback.QuickFeedbackEntry;
import com.findatex.validator.quickfeedback.QuickFeedbackStatus;
import com.findatex.validator.web.dto.QuickFeedbackResultDto;
import com.findatex.validator.web.dto.QuickFeedbackSubmitDto;
import com.findatex.validator.web.service.GeoIpService;
import com.findatex.validator.web.service.QuickFeedbackService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quick-feedback (1-5 star rating) submission. User-initiated, so validation
 * is synchronous with a structured status the UI can act on — but persistence
 * is asynchronous ({@link QuickFeedbackService}) and the success answer is
 * optimistic: a star rating has no user-visible downstream state, and blocking
 * the response on a slow database connection would ruin the low-barrier UX.
 * The flip side is that a 200 does not prove the row was persisted.
 *
 * <p>Per-IP rate limiting is enforced by {@code RateLimitFilter} (strict
 * bucket, anti-spam). {@code country_code} is derived here from the TCP source
 * IP; the raw IP is never persisted or logged. The comment text is never
 * logged.
 */
@Path("/api/quick-feedback")
@Produces(MediaType.APPLICATION_JSON)
public class QuickFeedbackResource {

    private static final Logger log = LoggerFactory.getLogger(QuickFeedbackResource.class);

    @Inject
    QuickFeedbackService quickFeedback;

    @Inject
    GeoIpService geoIp;

    @Context
    HttpServerRequest request;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response submit(QuickFeedbackSubmitDto dto) {
        if (!quickFeedback.enabled()) {
            return result(Response.Status.SERVICE_UNAVAILABLE, QuickFeedbackStatus.UNAVAILABLE);
        }
        if (dto == null
                || !QuickFeedbackEntry.isValidRating(dto.rating())
                || !QuickFeedbackEntry.isValidComment(dto.comment())) {
            return result(Response.Status.BAD_REQUEST, QuickFeedbackStatus.INVALID);
        }

        // Clamp to the DB CHECK's vocabulary — an unknown source must not fail
        // the async insert later.
        String source = "desktop".equalsIgnoreCase(trimmed(dto.source())) ? "desktop" : "web";
        String country = null;
        try {
            country = geoIp.countryFor(clientIp());
        } catch (RuntimeException e) {
            log.debug("Quick-feedback: geo lookup failed (ignored)");
        }
        quickFeedback.record(dto.rating(), QuickFeedbackEntry.normaliseComment(dto.comment()),
                source, dto.appVersion(), dto.templateId(), country);
        return result(Response.Status.OK, QuickFeedbackStatus.OK);
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.trim();
    }

    private String clientIp() {
        if (request == null || request.remoteAddress() == null) return null;
        return request.remoteAddress().host();
    }

    private Response result(Response.Status http, QuickFeedbackStatus status) {
        return Response.status(http).entity(new QuickFeedbackResultDto(status.wire())).build();
    }
}
