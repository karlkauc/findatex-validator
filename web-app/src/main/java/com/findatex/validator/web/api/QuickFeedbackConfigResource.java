package com.findatex.validator.web.api;

import com.findatex.validator.web.dto.QuickFeedbackConfigDto;
import com.findatex.validator.web.service.QuickFeedbackService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Tells the SPA whether to show the quick-feedback (star rating) widget.
 * Read-only GET with no abuse surface — intentionally outside the rate limiter
 * (mirrors {@code /api/newsletter-config}; the filter's POST-only guard keeps
 * this path unlimited despite sharing the {@code api/quick-feedback} prefix).
 */
@Path("/api/quick-feedback-config")
@Produces(MediaType.APPLICATION_JSON)
public class QuickFeedbackConfigResource {

    @Inject
    QuickFeedbackService quickFeedback;

    @GET
    public QuickFeedbackConfigDto get() {
        return new QuickFeedbackConfigDto(quickFeedback.enabled());
    }
}
