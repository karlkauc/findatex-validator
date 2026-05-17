package com.findatex.validator.web.api;

import com.findatex.validator.newsletter.EmailAddress;
import com.findatex.validator.newsletter.NewsletterStatus;
import com.findatex.validator.web.dto.NewsletterResultDto;
import com.findatex.validator.web.dto.NewsletterSubscribeDto;
import com.findatex.validator.web.service.NewsletterService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Newsletter sign-up. Unlike the anonymous usage-stats ingest this is a
 * user-initiated action with a personal datum, so it is <b>synchronous</b> and
 * returns a structured status the UI can act on.
 *
 * <p>The address is validated here (cheap reject before any outbound call),
 * then handed to {@link NewsletterService} → the external provider. It is
 * never stored or logged. Per-IP rate limiting is enforced by
 * {@code RateLimitFilter} (strict bucket, anti e-mail-bombing).
 */
@Path("/api/newsletter")
@Produces(MediaType.APPLICATION_JSON)
public class NewsletterResource {

    @Inject
    NewsletterService newsletter;

    @POST
    @Path("/subscribe")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response subscribe(NewsletterSubscribeDto dto) {
        if (!newsletter.enabled()) {
            return result(Response.Status.SERVICE_UNAVAILABLE, NewsletterStatus.UNAVAILABLE);
        }
        String email = dto == null ? null : EmailAddress.normalise(dto.email());
        if (!EmailAddress.isValid(email)) {
            return result(Response.Status.BAD_REQUEST, NewsletterStatus.INVALID_EMAIL);
        }

        NewsletterStatus status = newsletter.subscribe(email);
        Response.Status http = switch (status) {
            case INVALID_EMAIL -> Response.Status.BAD_REQUEST;
            case UNAVAILABLE -> Response.Status.SERVICE_UNAVAILABLE;
            default -> Response.Status.OK;
        };
        return result(http, status);
    }

    private Response result(Response.Status http, NewsletterStatus status) {
        return Response.status(http).entity(new NewsletterResultDto(status.wire())).build();
    }
}
