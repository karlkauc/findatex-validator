package com.findatex.validator.web.api.error;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Catch-all exception mapper for the REST layer. Two purposes:
 *
 * <ol>
 *   <li><b>Pass through {@link WebApplicationException}</b> — handlers that
 *       throw it have already built a sanitised {@link Response} (the
 *       orchestrator does this for 400 / 429 / 500). We just return that
 *       response unchanged.</li>
 *   <li><b>Sanitise everything else</b> — for unexpected throwables we log
 *       the full stack trace server-side and return a generic JSON 500. This
 *       prevents internal class names, file paths, or POI exception detail
 *       from leaking into the response body.</li>
 * </ol>
 */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(Throwable ex) {
        if (ex instanceof WebApplicationException w) {
            return w.getResponse();
        }
        log.error("Unhandled exception while processing request", ex);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                        "error", "internal_error",
                        "message", "Internal server error."))
                .build();
    }
}
