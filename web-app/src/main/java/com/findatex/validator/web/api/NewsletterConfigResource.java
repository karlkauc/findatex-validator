package com.findatex.validator.web.api;

import com.findatex.validator.web.dto.NewsletterConfigDto;
import com.findatex.validator.web.service.NewsletterService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Tells the SPA whether to show the newsletter sign-up form. Read-only GET with
 * no abuse surface — intentionally outside the rate limiter (mirrors
 * {@code /api/feedback-config}).
 */
@Path("/api/newsletter-config")
@Produces(MediaType.APPLICATION_JSON)
public class NewsletterConfigResource {

    @Inject
    NewsletterService newsletter;

    @GET
    public NewsletterConfigDto get() {
        return new NewsletterConfigDto(newsletter.enabled());
    }
}
