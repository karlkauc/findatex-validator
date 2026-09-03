package com.findatex.validator.web.api;

import com.findatex.validator.web.service.PublicUrls;
import com.findatex.validator.web.service.RulesPageRenderer;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The public help page: {@code /help} renders the bundled {@code HELP.md} — the
 * same document the desktop Help dialog and the web Help modal show — as an
 * ordinary server-rendered page.
 *
 * <p>This is where the landing copy went. It used to be static HTML below
 * {@code <div id="root">} in {@code index.html}, which meant three copies of
 * "what the templates are, what happens to your file, the FAQ" — one per UI plus
 * the page — drifting apart. Now there is one source, and this page makes it
 * crawlable without the React bundle, exactly like the rule reference.
 *
 * <p>The FAQPage structured data is emitted inline (search engines do not
 * follow external JSON-LD) and allow-listed in the CSP by hash, so the block is
 * a constant emitted byte-for-byte rather than something serialised at runtime.
 */
@Path("/help")
@Produces(MediaType.TEXT_HTML)
public class HelpPageResource {

    /** Changes only with a release, like the rule pages. */
    private static final String CACHE = "public, max-age=3600";

    /**
     * schema.org {@code FAQPage} markup for the FAQ section of {@code HELP.md}.
     *
     * <p>Every {@code text} here must equal, character for character, the
     * answer rendered from {@code HELP.md} — Google requires the answers to be
     * visible on the page, and {@code HelpPageTest} compares the two.
     *
     * <p>This block is allow-listed in {@code Content-Security-Policy} by its
     * sha256 hash ({@code application.properties}). <b>Editing a single
     * character here — whitespace included — changes that hash</b>; the browser
     * then silently drops the structured data. {@code HelpPageTest} recomputes
     * the hash from the served page and prints the correct value on mismatch.
     */
    static final String FAQ_JSON_LD = """
            {
              "@context": "https://schema.org",
              "@type": "FAQPage",
              "mainEntity": [
                {
                  "@type": "Question",
                  "name": "Do I need an account?",
                  "acceptedAnswer": {
                    "@type": "Answer",
                    "text": "No. There is no sign-up, no login and no e-mail address required. Pick a template, drop a file, get the result."
                  }
                },
                {
                  "@type": "Question",
                  "name": "Is my file stored anywhere?",
                  "acceptedAnswer": {
                    "@type": "Answer",
                    "text": "No. The upload is processed in memory and discarded as soon as the response is sent; the Excel report is deleted after the first download or five minutes, whichever comes first. No file content is logged."
                  }
                },
                {
                  "@type": "Question",
                  "name": "What does it cost?",
                  "acceptedAnswer": {
                    "@type": "Answer",
                    "text": "Nothing. It is a free, open-source project under the Apache 2.0 licence; the full source is on GitHub."
                  }
                },
                {
                  "@type": "Question",
                  "name": "Which file formats can I upload?",
                  "acceptedAnswer": {
                    "@type": "Answer",
                    "text": "Excel (.xlsx, .xlsm) and CSV, up to 25 MB. The sheet is matched against the spec by its field numbers, so column order does not have to be exact."
                  }
                },
                {
                  "@type": "Question",
                  "name": "Can I validate confidential fund data?",
                  "acceptedAnswer": {
                    "@type": "Answer",
                    "text": "Use the desktop app for that. It runs the same validation engine entirely on your machine, with no upload and no network access unless you enable the optional GLEIF/OpenFIGI lookups."
                  }
                },
                {
                  "@type": "Question",
                  "name": "Does a score of 100 mean my file is accepted?",
                  "acceptedAnswer": {
                    "@type": "Answer",
                    "text": "No. It means the checks implemented here found no errors. Counterparties apply their own additional rules, and this tool does not implement regulatory interpretation. Treat it as a first pass, not as a substitute for your recipient's validation."
                  }
                },
                {
                  "@type": "Question",
                  "name": "Can I run it in-house?",
                  "acceptedAnswer": {
                    "@type": "Answer",
                    "text": "Yes. The web app ships as a Docker image and the desktop app as a native installer for Windows, macOS and Linux. Both are on GitHub."
                  }
                }
              ]
            }
            """;

    @Inject
    HelpResource help;

    @Inject
    RulesPageRenderer renderer;

    @Inject
    PublicUrls urls;

    @Context
    HttpServerRequest request;

    @GET
    public Response page() {
        String body = renderer.markdownToHtml(help.helpMarkdown())
                + "<script type=\"application/ld+json\">" + FAQ_JSON_LD + "</script>";

        return Response.ok(renderer.page(
                        "FinDatEx Validator — Help & FAQ",
                        "What the FinDatEx Validator checks in TPT, EET, EMT and EPT files, which "
                                + "spec versions and profiles are supported, what happens to an "
                                + "uploaded file, how the quality score is computed, and answers to "
                                + "common questions.",
                        urls.absolute(request, "/help"),
                        "<a href=\"/\">Validator</a> › Help",
                        body), MediaType.TEXT_HTML_TYPE)
                .header("Cache-Control", CACHE)
                .build();
    }
}
