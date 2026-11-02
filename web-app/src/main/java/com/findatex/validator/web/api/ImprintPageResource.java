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

/** {@code /imprint}: server-rendered, not indexed. See {@link LegalPages}. */
@Path("/imprint")
@Produces(MediaType.TEXT_HTML)
public class ImprintPageResource {

    @Inject
    LegalPages pages;

    @Context
    HttpServerRequest request;

    @GET
    public Response page() {
        return pages.render(request, "imprint", "Imprint",
                "Who runs the FinDatEx Validator, how to get in touch, and the terms the free tool is offered on.");
    }
}
