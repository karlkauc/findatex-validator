package com.findatex.validator.web.api;

import com.findatex.validator.web.config.WebConfig;
import com.findatex.validator.web.dto.FeedbackConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Exposes the operator-configured "report a false positive" target. Read-only
 * GET — there is no server-side issue creation and no token, so this endpoint
 * carries no abuse surface and is intentionally outside the rate limiter.
 */
@Path("/api/feedback-config")
@Produces(MediaType.APPLICATION_JSON)
public class FeedbackConfigResource {

    @Inject
    WebConfig config;

    @GET
    public FeedbackConfig get() {
        return new FeedbackConfig(config.feedbackGithubRepo().orElse(null));
    }
}
