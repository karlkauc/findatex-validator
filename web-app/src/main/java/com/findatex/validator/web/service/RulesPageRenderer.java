package com.findatex.validator.web.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;

/**
 * Renders the public rule-reference pages and the help page: Markdown to HTML,
 * wrapped in a self-contained page shell.
 *
 * <p>These pages are plain server-rendered HTML with no React and no client-side
 * routing — that is the entire point. Their value is being readable by a
 * crawler and by someone who followed a search result, before (and without) a
 * 460 kB JavaScript bundle. The only script is the page-view beacon.
 *
 * <p>The styling is an inline {@code <style>} block rather than the SPA's
 * Tailwind bundle: these pages are not part of the Vite build, and CSP already
 * allows inline styles. It is deliberately close to the app's palette so
 * following a link into the validator does not feel like a different site.
 */
@ApplicationScoped
public class RulesPageRenderer {

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    // The generator never hard-wraps prose, so every soft line break in the
    // source is a real one — the "FundsXML path / Codification / Applicability /
    // Definition" block runs together into one paragraph without this.
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .softbreak("<br />")
            .build();
    // Hand-written prose (HELP.md) is hard-wrapped at ~75 columns; there a soft
    // line break is just the author's editor width and must flow.
    private static final HtmlRenderer PROSE_RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .softbreak("\n")
            .build();

    private static final String STYLE = """
            :root { color-scheme: light; }
            * { box-sizing: border-box; }
            body { margin: 0; background: #f8fafc; color: #0f172a;
                   font: 15px/1.65 system-ui, -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
            a { color: #1d3a5c; }
            header.site { background: linear-gradient(180deg, #1d3a5c, #173049); color: #fff; }
            header.site .bar { max-width: 60rem; margin: 0 auto; padding: 14px 24px;
                   display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
            header.site a { color: #fff; text-decoration: none; font-weight: 600; }
            header.site .tag { color: #dde6f0; font-size: 13px; font-weight: 400; }
            header.site a.nav { color: #dde6f0; font-size: 13px; font-weight: 500; }
            header.site a.nav:hover { color: #fff; text-decoration: underline; }
            header.site .cta { margin-left: auto; border: 1px solid rgba(255,255,255,.25);
                   background: rgba(255,255,255,.1); border-radius: 6px; padding: 6px 12px; font-size: 13px; }
            main { max-width: 60rem; margin: 0 auto; padding: 28px 24px 64px; }
            nav.crumbs { font-size: 13px; color: #64748b; margin-bottom: 18px; }
            nav.crumbs a { color: #475569; }
            h1 { font-size: 26px; line-height: 1.25; margin: 0 0 8px; }
            h2 { font-size: 19px; margin: 32px 0 8px; padding-top: 8px; border-top: 1px solid #e2e8f0; }
            h3 { font-size: 16px; margin: 22px 0 6px; }
            p, li { color: #334155; }
            code { background: #eef2f7; border-radius: 4px; padding: 1px 5px; font-size: 13px;
                   font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
            pre { background: #eef2f7; padding: 12px; border-radius: 6px; overflow-x: auto; }
            blockquote { margin: 16px 0; padding: 8px 16px; border-left: 3px solid #cbd5e1;
                   color: #475569; background: #fff; }
            .scroll { overflow-x: auto; }
            table { border-collapse: collapse; width: 100%; font-size: 13.5px; margin: 12px 0; background: #fff; }
            th, td { border: 1px solid #e2e8f0; padding: 6px 10px; text-align: left; vertical-align: top; }
            th { background: #f1f5f9; font-weight: 600; }
            .cta-box { margin: 36px 0 8px; padding: 18px 20px; background: #fff;
                   border: 1px solid #e2e8f0; border-radius: 8px; }
            .cta-box a.button { display: inline-block; margin-top: 10px; background: #1d3a5c; color: #fff;
                   text-decoration: none; border-radius: 6px; padding: 9px 16px; font-weight: 600; font-size: 14px; }
            ul.fields { list-style: none; padding: 0; display: grid; gap: 6px;
                   grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr)); }
            ul.fields a { display: block; background: #fff; border: 1px solid #e2e8f0; border-radius: 6px;
                   padding: 8px 10px; text-decoration: none; font-size: 13.5px; }
            ul.fields a:hover { border-color: #94a3b8; }
            ul.fields .num { font-weight: 600; color: #0f172a; }
            ul.fields .name { color: #64748b; display: block;
                   font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px;
                   overflow-wrap: anywhere; }
            footer.site { border-top: 1px solid #e2e8f0; background: #fff; }
            footer.site div { max-width: 60rem; margin: 0 auto; padding: 20px 24px 40px;
                   font-size: 12px; color: #64748b; }
            """;

    /** Markdown (GFM tables) to HTML, with wide tables kept scrollable. */
    public String markdownToHtml(String markdown) {
        return render(RENDERER, markdown);
    }

    /** Like {@link #markdownToHtml} but soft line breaks flow — for hand-wrapped prose such as HELP.md. */
    public String proseToHtml(String markdown) {
        return render(PROSE_RENDERER, markdown);
    }

    private static String render(HtmlRenderer renderer, String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        String html = renderer.render(PARSER.parse(markdown));
        // The rule tables are wide; without this the page body scrolls sideways
        // on a phone instead of the table doing it.
        return html.replace("<table>", "<div class=\"scroll\"><table>")
                .replace("</table>", "</table></div>");
    }

    /**
     * Wraps rendered content in the full document. {@code canonical} is the
     * absolute URL of this page — without it these pages would compete with
     * each other across hostnames exactly like the SPA used to.
     */
    public String page(String title, String description, String canonical,
                       String crumbs, String bodyHtml) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="theme-color" content="#173049">
                <link rel="icon" type="image/x-icon" href="/favicon.ico">
                <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32.png">
                <title>%s</title>
                <meta name="description" content="%s">
                <link rel="canonical" href="%s">
                <meta property="og:type" content="article">
                <meta property="og:title" content="%s">
                <meta property="og:description" content="%s">
                <meta property="og:url" content="%s">
                <meta property="og:image" content="%s">
                <meta name="twitter:card" content="summary_large_image">
                <style>%s</style>
                </head>
                <body>
                <header class="site"><div class="bar">
                  <a href="/">FinDatEx Validator</a>
                  <span class="tag">TPT · EET · EMT · EPT</span>
                  <a class="nav" href="/help">Help</a>
                  <a class="nav" href="/rules">Rules</a>
                  <a class="cta" href="/">Validate a file</a>
                </div></header>
                <main>
                <nav class="crumbs">%s</nav>
                %s
                <div class="cta-box">
                  <strong>Check your own file against these rules</strong>
                  <p>Upload a TPT, EET, EMT or EPT file and get a quality score, every finding
                     grouped by rule, and an Excel report. No login; the file is never stored.</p>
                  <a class="button" href="/">Open the validator</a>
                </div>
                </main>
                <footer class="site"><div>
                  Generated from the official FinDatEx spec sheets bundled with the validator.
                  <strong>Not an official FinDatEx tool</strong> — a private, open-source project,
                  not affiliated with or endorsed by the FinDatEx initiative. Provided as-is,
                  without warranty; validation results may be incomplete or wrong.
                </div></footer>
                <script src="/rules-page.js" defer></script>
                </body>
                </html>
                """.formatted(
                escape(title), escape(description), escape(canonical),
                escape(title), escape(description), escape(canonical),
                escape(origin(canonical) + "/og-image.png"),
                STYLE, crumbs, bodyHtml);
    }

    /** Scheme + host of an absolute URL, for building sibling asset URLs. */
    private static String origin(String url) {
        int slash = url.indexOf('/', url.indexOf("//") + 2);
        return slash < 0 ? url : url.substring(0, slash);
    }

    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
