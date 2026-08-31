# Findability — SEO, link previews and traffic measurement

What is in place, what it depends on, and what still has to be done by hand.
The web app is a single-page app with no login and one public URL, so almost
everything here is a property of `index.html`, a static file, or one config
value — there is no CMS to configure.

## The canonical host

`www.findatex-validator.eu`. Four places have to agree on it:

| Where | What |
|---|---|
| `web-app/src/main/frontend/index.html` | `<link rel="canonical">`, `og:url`, JSON-LD `url` |
| `web-app/src/main/resources/META-INF/resources/robots.txt` | `Sitemap:` line |
| `web-app/src/main/resources/META-INF/resources/sitemap.xml` | every `<loc>` |
| `FINDATEX_WEB_CANONICAL_HOST` (Cloud Run) | drives the 301 in `CanonicalHostFilter` |

`SeoMetadataTest` fails if the first three drift apart. The env var is set in
`.github/workflows/deploy-cloudrun.yml`; leave it **unset** for self-hosted
instances (see `docs/DEPLOY_CLOUDRUN.md`).

Without the redirect the service answers on three hostnames — apex, `www`, and
its `*.run.app` URL — with byte-identical HTML, which a crawler treats as three
competing copies of one site.

## Static files a crawler expects

`robots.txt` lives under `web-app/src/main/resources/META-INF/resources/` (the
**backend** classpath, not the Vite `public/` dir) so it exists even in
`-P backend-only` builds. It used to be swallowed by `SpaFallbackResource`,
which answered every unmatched path with 200 + the SPA shell; that resource now
returns a real 404 for file-like paths, and must keep doing so — otherwise
every stale asset URL becomes a soft-404 "page" with identical content.

`sitemap.xml` is **generated** by `SitemapResource`: the app, `/rules`, one
entry per template version and one per documented field — ~2000 URLs that
change with every spec version. Never re-add a static `sitemap.xml`: a file of
that name is served by the static-resource handler and wins over the resource.

## The rule reference (`/rules`)

`RulesPageResource` serves the generated rule documentation as ordinary
server-rendered HTML:

| URL | Content |
|---|---|
| `/rules` | index of the eight documented template versions |
| `/rules/{slug}` | one template version: scoring, profiles, general and cross-field rules, plus links to every field |
| `/rules/{slug}/field/{num}` | one field: definition, flag per profile, codification, every rule that can fire on it |

`RuleDocs` splits the generated Markdown (`docs/rules/*.md`, bundled at
`help/rules/`) on the generator's own structure — `## 5. Per-field catalog`,
then `### Field N — name`. That coupling is deliberate and covered by
`RuleDocsTest`: if the generator's shape moves, the pages would otherwise go
silently empty.

These pages carry no application bundle. Their point is being readable by a
crawler and by someone arriving from a search result, so they render with an
inline stylesheet and one small script (`/rules-page.js`, the page-view
beacon). Each has its own `<title>`, description (the spec's own field
definition) and canonical, and a call to action back into the validator —
without it they would just be documentation.

## Link previews

`og:*` and `twitter:*` tags in `index.html`, with a 1200x630 card at
`/og-image.png`. Regenerate the card with `python3 tools/generate_og_image.py`
— never edit the PNG by hand; it reuses the app icon from
`tools/generate_icon.py` so the two cannot drift apart.

This is what LinkedIn, Teams and Slack render, which for a B2B tool is where
most links are actually shared.

## Structured data and the CSP hash

`index.html` carries one inline `<script type="application/ld+json">`: a
`@graph` with a schema.org `SoftwareApplication` object and a `FAQPage`. Search
engines only read JSON-LD inline, and `script-src` is strict `'self'` with no
`'unsafe-inline'` — so the block is allow-listed by a **`sha256-` hash** in
`application.properties`.

The FAQ answers in the markup must stay identical to the ones rendered in the
page body — Google treats markup-only answers as a structured-data violation.
`SeoMetadataTest` compares the two.

**Editing one character of that block — whitespace included — invalidates the
hash and browsers silently drop the structured data.** `SeoMetadataTest`
recomputes it and prints the correct value in the failure message.

## Page content

The landing copy — what the templates are, what happens to an uploaded file,
how the score is computed, the FAQ, and the "not an official FinDatEx tool"
disclaimer — lives as **plain HTML in `index.html`**, below `<div id="root">`,
not as a React component. It is then part of the initial payload: indexable
without JavaScript execution and readable before the bundle boots. Tailwind
compiles the classes used there (`tailwind.config.js` lists `./index.html`).

`SeoMetadataTest` asserts that the latest version of every template is named in
that copy, so adding a spec version fails the build until the page mentions it.

## Measuring whether any of it works

Two numbers, one database (`findatex_stats`, see `docs/USAGE_STATS.md`):

- **`page_view`** — one row per page load, written from the SPA beacon.
- **`usage_event`** — one row per validation run.

`tools/usage_report.py` prints them together under **Traffic**, with a
`pct_validated` column. That ratio is the diagnosis: few views means a reach
problem (promotion, content), many views and few runs means a landing-page
problem.

The counter stores no cookie, no id and no IP, so it needs no consent banner —
and it is client-side precisely so that crawlers do not end up being counted as
visitors.

## Manual steps (not automatable from this repo)

### Google Search Console

Needed to submit the sitemap and to see which queries the site appears for.

1. <https://search.google.com/search-console> → *Add property* → **Domain**
   property `findatex-validator.eu` (covers apex and `www`).
2. It asks for a **DNS TXT record**. Add it at the domain registrar. DNS
   verification is preferred here over the HTML-file and meta-tag methods:
   both of those would have to be redeployed with the container, and the
   HTML-file method now correctly 404s for unknown file paths.
3. After verification: *Sitemaps* → submit `sitemap.xml`.
4. Bing Webmaster Tools can import the verified Google property in one click.

### After the first deploy

Worth checking once, because all of it fails silently:

- `curl -sI https://findatex-validator.eu` → `301` to the `www` host.
- `curl -s https://www.findatex-validator.eu/robots.txt` → plain text, not HTML.
- `curl -s https://www.findatex-validator.eu/sitemap.xml | grep -c '<url>'` →
  ~2000, with `https://www.findatex-validator.eu` as the host of every `<loc>`.
- `https://www.findatex-validator.eu/rules/tpt-v8-0/field/26` renders with
  JavaScript disabled.
- Paste the URL into the LinkedIn Post Inspector — the card must show the image.
- Browser console on the live site: no CSP violation for the JSON-LD block.
- `python3 tools/usage_report.py --days 7` → the **Traffic** section counts up.

## Open

Ordered by expected effect, not effort:

1. **`docs/TPT_V7_TO_V8_CHANGES.md` as a public page.** TPT V8 shipped
   2026-05-26; "what changes from V7 to V8" is a time-limited traffic peak.
2. **A German variant.** The audience sits in DE/AT/CH/LU; roughly double the
   search volume for the same content, at the cost of `hreflang` upkeep.
3. **Watch the rule pages in Search Console.** ~2000 pages generated from one
   template is exactly the shape Google can decide to treat as thin content.
   The signal to watch is Coverage: many "Crawled — currently not indexed"
   field pages would mean the per-field split was too fine, and the fix is to
   fold fields back into per-version pages rather than to add more of them.

## Related

- `docs/USAGE_STATS.md` — the stats DB, `page_view` and `usage_event` schema
- `docs/DEPLOY_CLOUDRUN.md` — `FINDATEX_WEB_CANONICAL_HOST` in the deploy
