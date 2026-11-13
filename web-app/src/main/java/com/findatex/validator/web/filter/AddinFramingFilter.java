package com.findatex.validator.web.filter;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Lets Excel embed the add-in task pane under {@code /addin/} (see
 * {@code docs/EXCEL_ADDIN.md}) — and only there.
 *
 * <p>The global headers forbid framing ({@code X-Frame-Options: DENY},
 * {@code frame-ancestors 'none'}) and foreign scripts ({@code script-src 'self'}).
 * Excel on the web shows a task pane in an iframe on an Office host, and the
 * pane must load {@code office.js} from Microsoft's CDN (a store/sideload
 * requirement — it cannot be bundled). So for {@code /addin/*}: no
 * {@code X-Frame-Options}, a CSP that allows the Office hosts as frame
 * ancestors and the Office.js CDN as script/connect source, everything else
 * as strict as the global policy (no JSON-LD hashes needed — the pane has no
 * inline script). The pane and the manifest are also served {@code no-cache}:
 * the static default is {@code immutable} for a day, which would pin a stale
 * manifest or pane in Office's cache.
 *
 * <p>Rewritten in a headers-end handler, like {@link SpaOpenerPolicyFilter},
 * because the configured headers are added after this filter has run.
 */
@ApplicationScoped
public class AddinFramingFilter {

    /** Ahead of the static-resource route, like the other header filters. */
    private static final int PRIORITY = 290;

    static final String PREFIX = "/addin/";

    static final String OFFICE_JS_ORIGIN = "https://appsforoffice.microsoft.com";

    /** Hosts Office uses to frame add-ins (Excel on the web, Microsoft 365, SharePoint). */
    static final String FRAME_ANCESTORS = "https://*.officeapps.live.com https://*.office.com "
            + "https://*.office365.com https://*.microsoft.com https://*.sharepoint.com "
            + "https://*.cloud.microsoft";

    static final String CSP = "default-src 'self'; "
            + "script-src 'self' " + OFFICE_JS_ORIGIN + "; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "font-src 'self'; "
            + "connect-src 'self' " + OFFICE_JS_ORIGIN + "; "
            + "frame-ancestors 'self' " + FRAME_ANCESTORS + "; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    void register(@Observes Filters filters) {
        filters.register(AddinFramingFilter::allowOfficeFraming, PRIORITY);
    }

    static boolean isAddinPath(String path) {
        return path != null && path.startsWith(PREFIX);
    }

    private static boolean isUncachedDocument(String path) {
        return path.endsWith("/") || path.endsWith(".html") || path.endsWith(".xml");
    }

    private static void allowOfficeFraming(RoutingContext rc) {
        String path = rc.request().path();
        if (isAddinPath(path)) {
            boolean noCache = isUncachedDocument(path);
            rc.addHeadersEndHandler(v -> {
                var headers = rc.response().headers();
                headers.remove("X-Frame-Options");
                headers.set("Content-Security-Policy", CSP);
                if (noCache) headers.set("Cache-Control", "no-cache");
            });
        }
        rc.next();
    }
}
