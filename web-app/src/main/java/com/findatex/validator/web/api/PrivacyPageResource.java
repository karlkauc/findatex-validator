package com.findatex.validator.web.api;

import com.findatex.validator.web.service.LegalPages;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** {@code /privacy}: server-rendered, not indexed. See {@link LegalPages}. */
@Path("/privacy")
@Produces(MediaType.TEXT_HTML)
public class PrivacyPageResource {

    @Inject
    LegalPages pages;

    @Context
    HttpServerRequest request;

    @GET
    public Response page() {
        return pages.render(request, "privacy", "Privacy policy",
                "What data the FinDatEx Validator processes, why, for how long and who receives it, and what rights you have.");
    }
}
