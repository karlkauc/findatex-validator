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

`robots.txt` and `sitemap.xml` live under
`web-app/src/main/resources/META-INF/resources/` (the **backend** classpath, not
the Vite `public/` dir) so they exist even in `-P backend-only` builds. They
used to be swallowed by `SpaFallbackResource`, which answered every unmatched
path with 200 + the SPA shell; that resource now returns a real 404 for
file-like paths, and must keep doing so — otherwise every stale asset URL
becomes a soft-404 "page" with identical content.

The sitemap currently lists one URL. It becomes useful when the generated rule
reference gets real URLs (see [Open](#open)).

## Link previews

`og:*` and `twitter:*` tags in `index.html`, with a 1200x630 card at
`/og-image.png`. Regenerate the card with `python3 tools/generate_og_image.py`
— never edit the PNG by hand; it reuses the app icon from
`tools/generate_icon.py` so the two cannot drift apart.

This is what LinkedIn, Teams and Slack render, which for a B2B tool is where
most links are actually shared.

## Structured data and the CSP hash

`index.html` carries one inline `<script type="application/ld+json">` with a
schema.org `SoftwareApplication` object. Search engines only read JSON-LD
inline, and `script-src` is strict `'self'` with no `'unsafe-inline'` — so the
block is allow-listed by a **`sha256-` hash** in
`application.properties`.

**Editing one character of that block — whitespace included — invalidates the
hash and browsers silently drop the structured data.** `SeoMetadataTest`
recomputes it and prints the correct value in the failure message.

No `FAQPage` markup yet: Google requires the answers to be visible on the page,
and they are not (see [Open](#open)).

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
- Paste the URL into the LinkedIn Post Inspector — the card must show the image.
- Browser console on the live site: no CSP violation for the JSON-LD block.
- `python3 tools/usage_report.py --days 7` → the **Traffic** section counts up.

## Open

Ordered by expected effect, not effort:

1. **Publish the rule reference as real pages.** `docs/rules/*.md` is ~57k
   lines of specific, hard-to-find content that currently exists only inside a
   modal (`GET /api/help/rules/{slug}`), invisible to search engines. Generated
   URLs like `/rules/tpt-v8-0` target exactly the long-tail queries the audience
   types ("TPT field 117 mandatory", "EET codification invalid"). `RuleDocGenerator`
   already produces the content; it needs an HTML output and sitemap entries.
2. **Visible landing-page text.** The crawler currently sees no prose at all —
   no explanation of what a TPT/EET/EMT/EPT file is, who the tool is for, or
   what happens to an uploaded file. Adding it also unlocks `FAQPage` markup.
3. **`docs/TPT_V7_TO_V8_CHANGES.md` as a public page.** TPT V8 shipped
   2026-05-26; "what changes from V7 to V8" is a time-limited traffic peak.
4. **A German variant.** The audience sits in DE/AT/CH/LU; roughly double the
   search volume for the same content, at the cost of `hreflang` upkeep.

## Related

- `docs/USAGE_STATS.md` — the stats DB, `page_view` and `usage_event` schema
- `docs/DEPLOY_CLOUDRUN.md` — `FINDATEX_WEB_CANONICAL_HOST` in the deploy
