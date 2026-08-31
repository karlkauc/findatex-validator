package com.findatex.validator.web.api;

import com.findatex.validator.web.service.PublicUrls;
import com.findatex.validator.web.service.RuleDocs;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import java.util.HashSet;
import java.util.Set;

import static com.findatex.validator.web.service.RulesPageRenderer.escape;

/**
 * Generated sitemap.
 *
 * <p>It used to be a static file with a single URL. Once the rule reference
 * became real pages that stopped working: there are ~2000 of them, they change
 * with every spec version, and a hand-maintained list would be wrong within one
 * release. It replaces the static file entirely — a file of the same name would
 * win over this resource in the static-resource handler.
 *
 * <p>Priorities are the honest ordering rather than a lever: the app itself,
 * then the per-version references, then fields of the current spec version,
 * then fields of superseded versions — which are near-identical to their
 * successors and should not compete with them.
 */
@Path("/sitemap.xml")
public class SitemapResource {

    @Inject
    RuleDocs docs;

    @Inject
    PublicUrls urls;

    @Context
    HttpServerRequest request;

    @GET
    @Produces("application/xml")
    public Response sitemap() {
        String origin = urls.origin(request);
        StringBuilder xml = new StringBuilder(256 * 1024);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        url(xml, origin + "/", "weekly", "1.0");
        if (!docs.index().isEmpty()) url(xml, origin + "/rules", "monthly", "0.8");

        Set<String> currentTemplates = new HashSet<>();
        for (RuleDocs.DocRef ref : docs.index()) {
            // index.json lists newest first per template.
            boolean current = currentTemplates.add(ref.templateId());
            url(xml, origin + "/rules/" + ref.slug(), "monthly", current ? "0.8" : "0.6");

            String fieldPriority = current ? "0.5" : "0.3";
            docs.doc(ref.slug()).ifPresent(doc -> doc.fields().values().forEach(f ->
                    url(xml, origin + "/rules/" + ref.slug() + "/field/" + f.num(),
                            "yearly", fieldPriority)));
        }

        xml.append("</urlset>\n");
        return Response.ok(xml.toString(), "application/xml")
                .header("Cache-Control", "public, max-age=86400")
                .build();
    }

    private static void url(StringBuilder xml, String loc, String changefreq, String priority) {
        xml.append("  <url><loc>").append(escape(loc)).append("</loc>")
                .append("<changefreq>").append(changefreq).append("</changefreq>")
                .append("<priority>").append(priority).append("</priority></url>\n");
    }
}
