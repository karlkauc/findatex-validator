package com.findatex.validator.web.api;

import com.findatex.validator.web.service.PublicUrls;
import com.findatex.validator.web.service.RuleDocs;
import com.findatex.validator.web.service.RulesPageRenderer;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.findatex.validator.web.service.RulesPageRenderer.escape;

/**
 * The public rule reference: {@code /rules}, {@code /rules/{slug}} and
 * {@code /rules/{slug}/field/{num}}.
 *
 * <p>This content already existed — ~57k lines generated from the spec sheets —
 * but only inside a modal behind {@code GET /api/help/rules/…}, where no search
 * engine could reach it. These are ordinary server-rendered pages: no
 * JavaScript needed to read them, one {@code <title>} and canonical each, and a
 * link back into the validator.
 *
 * <p>The split follows how people search. A template version's intro (scoring,
 * profiles, general and cross-field rules) answers "TPT validation rules"; a
 * per-field page answers "TPT field 117 mandatory", which is the query this
 * project is uniquely able to answer.
 */
@Path("/rules")
@Produces(MediaType.TEXT_HTML)
public class RulesPageResource {

    /** These pages change only when a release regenerates the docs. */
    private static final String CACHE = "public, max-age=3600";

    @Inject
    RuleDocs docs;

    @Inject
    RulesPageRenderer renderer;

    @Inject
    PublicUrls urls;

    @Context
    HttpServerRequest request;

    @GET
    public Response index() {
        List<RuleDocs.DocRef> refs = docs.index();
        if (refs.isEmpty()) throw new NotFoundException();

        // Group by template so the page reads as four references with two
        // versions each, not eight unrelated documents.
        Map<String, List<RuleDocs.DocRef>> byTemplate = new LinkedHashMap<>();
        for (RuleDocs.DocRef ref : refs) {
            byTemplate.computeIfAbsent(ref.templateDisplayName(), k -> new java.util.ArrayList<>()).add(ref);
        }

        StringBuilder body = new StringBuilder();
        body.append("<h1>FinDatEx validation rules</h1>")
                .append("<p>Every check this validator applies, generated from the official spec "
                        + "sheets: which fields are mandatory per profile, what each codification "
                        + "accepts, and every cross-field rule with its severity and score impact. "
                        + "One reference per template version.</p>");
        byTemplate.forEach((template, versions) -> {
            body.append("<h2>").append(escape(template)).append("</h2><ul>");
            for (RuleDocs.DocRef ref : versions) {
                int fields = docs.doc(ref.slug()).map(d -> d.fields().size()).orElse(0);
                body.append("<li><a href=\"/rules/").append(escape(ref.slug())).append("\">")
                        .append(escape(ref.label())).append("</a> — ")
                        .append(fields).append(" documented fields</li>");
            }
            body.append("</ul>");
        });

        return html(renderer.page(
                "FinDatEx validation rules — TPT, EET, EMT and EPT reference",
                "Field-by-field reference of every rule the FinDatEx Validator applies to TPT, "
                        + "EET, EMT and EPT files: mandatory flags per profile, codifications and "
                        + "cross-field rules.",
                urls.absolute(request, "/rules"),
                "<a href=\"/\">Validator</a> › Rules",
                body.toString()));
    }

    @GET
    @Path("/{slug}")
    public Response document(@PathParam("slug") String slug) {
        RuleDocs.DocRef ref = docs.ref(slug).orElseThrow(NotFoundException::new);
        RuleDocs.Doc doc = docs.doc(slug).orElseThrow(NotFoundException::new);

        StringBuilder body = new StringBuilder(renderer.markdownToHtml(doc.intro()));
        if (!doc.fields().isEmpty()) {
            body.append("<h2>Field reference</h2>")
                    .append("<p>One page per field, with its flag in every profile, the "
                            + "codification it must match and every rule that can fire on it.</p>")
                    .append("<ul class=\"fields\">");
            doc.fields().values().forEach(f -> body
                    .append("<li><a href=\"/rules/").append(escape(slug)).append("/field/")
                    .append(escape(f.num())).append("\"><span class=\"num\">Field ")
                    .append(escape(f.num())).append("</span><span class=\"name\">")
                    .append(escape(f.name())).append("</span></a></li>"));
            body.append("</ul>");
        }

        return html(renderer.page(
                ref.templateDisplayName() + " " + ref.version()
                        + " validation rules — FinDatEx Validator",
                "Every check applied to a " + ref.templateDisplayName() + " " + ref.version()
                        + " file: mandatory and conditional fields per profile, formats, closed "
                        + "code lists and cross-field rules, with the score impact of each.",
                urls.absolute(request, "/rules/" + slug),
                "<a href=\"/\">Validator</a> › <a href=\"/rules\">Rules</a> › "
                        + escape(ref.label()),
                body.toString()));
    }

    @GET
    @Path("/{slug}/field/{num}")
    public Response field(@PathParam("slug") String slug, @PathParam("num") String num) {
        RuleDocs.DocRef ref = docs.ref(slug).orElseThrow(NotFoundException::new);
        RuleDocs.Doc doc = docs.doc(slug).orElseThrow(NotFoundException::new);
        RuleDocs.Field field = doc.field(num).orElseThrow(NotFoundException::new);

        String heading = ref.templateDisplayName() + " " + ref.version()
                + " field " + field.num();
        String body = "<h1>" + escape(heading) + "</h1>"
                + "<p><code>" + escape(field.name()) + "</code></p>"
                + renderer.markdownToHtml(field.markdown());

        // The spec's own definition is the description: a hand-written one per
        // field is not maintainable at this count, and it is what a searcher
        // was looking for anyway.
        String description = field.definition() != null && !field.definition().isBlank()
                ? field.definition()
                : heading + " (" + field.name() + ") — mandatory flags per profile, codification "
                        + "and the validation rules that apply to it.";

        return html(renderer.page(
                heading + " — " + field.name(),
                truncate(description, 300),
                urls.absolute(request, "/rules/" + slug + "/field/" + field.num()),
                "<a href=\"/\">Validator</a> › <a href=\"/rules\">Rules</a> › "
                        + "<a href=\"/rules/" + escape(slug) + "\">" + escape(ref.label())
                        + "</a> › Field " + escape(field.num()),
                body));
    }

    private static String truncate(String s, int max) {
        String flat = s.replaceAll("\\s+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, max - 1).strip() + "…";
    }

    private static Response html(String body) {
        return Response.ok(body, MediaType.TEXT_HTML_TYPE)
                .header("Cache-Control", CACHE)
                .build();
    }
}
