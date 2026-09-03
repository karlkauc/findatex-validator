package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

/**
 * The crawler-facing surface: robots.txt and sitemap.xml must be served as real
 * files, and unknown file-like paths must 404 instead of silently returning the
 * SPA shell.
 *
 * <p>Regression guard for a live defect: both files used to be swallowed by
 * {@link SpaFallbackResource}, so {@code /robots.txt} answered 200 with HTML —
 * an invalid robots.txt and an unparseable sitemap.
 */
@QuarkusTest
class SeoResourcesTest {

    @Test
    void robotsTxtIsServedAsPlainTextWithASitemapReference() {
        given()
                .when().get("/robots.txt")
                .then()
                .statusCode(200)
                .contentType(startsWith("text/plain"))
                .body(containsString("User-agent: *"))
                .body(containsString("Sitemap: https://www.findatex-validator.eu/sitemap.xml"));
    }

    @Test
    void sitemapIsGeneratedAndListsTheAppTheHelpAndTheRulePages() {
        // Generated, not a static file: there are ~2000 rule pages and they
        // change with every spec version, so a hand-written list would be
        // stale within one release.
        given()
                .when().get("/sitemap.xml")
                .then()
                .statusCode(200)
                .contentType(containsString("xml"))
                .body(containsString("<urlset"))
                .body(containsString("/help</loc>"))
                .body(containsString("/rules</loc>"))
                .body(containsString("/rules/tpt-v8-0</loc>"))
                .body(containsString("/rules/tpt-v8-0/field/26</loc>"));
    }

    @Test
    void supersededSpecVersionsRankBelowCurrentOnesInTheSitemap() {
        // A superseded version's field pages are near-identical to their
        // successors'; promoting both equally makes them compete.
        String sitemap = given().when().get("/sitemap.xml")
                .then().statusCode(200).extract().body().asString();

        assertThat(lineFor(sitemap, "/rules/tpt-v8-0/field/26<"))
                .contains("<priority>0.5<");
        assertThat(lineFor(sitemap, "/rules/tpt-v7-0/field/26<"))
                .contains("<priority>0.3<");
    }

    private static String lineFor(String sitemap, String needle) {
        return sitemap.lines().filter(l -> l.contains(needle)).findFirst()
                .orElseThrow(() -> new AssertionError("no sitemap entry for " + needle));
    }

    @Test
    void theSpaShellIsNotCachedLikeAnImmutableAsset() {
        // index.html names the hashed bundle it loads. Caching it for a day
        // (which is what the static handler does by default, and what shipped)
        // means a returning visitor keeps the previous app after a deploy, and
        // a blank page once the old assets are gone.
        given().when().get("/")
                .then().statusCode(200)
                .header("Cache-Control", containsString("no-cache"));

        given().when().get("/index.html")
                .then().statusCode(200)
                .header("Cache-Control", containsString("no-cache"));
    }

    @Test
    void hashedAssetsStayImmutable() {
        // The flip side: those must keep their long cache, or every page load
        // re-downloads 460 kB.
        String shell = given().when().get("/").then().extract().body().asString();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("/assets/index-[A-Za-z0-9_-]+\\.js").matcher(shell);
        assertThat(m.find()).as("index.html must reference a hashed bundle").isTrue();

        given().when().get(m.group())
                .then().statusCode(200)
                .header("Cache-Control", containsString("max-age"))
                .header("Cache-Control", containsString("immutable"));
    }

    @Test
    void unknownFileLikePathsAre404NotTheSpaShell() {
        // A stale asset hash, a probe for a file we do not have: answering these
        // with 200 + index.html creates unbounded soft-404s for crawlers and
        // hands browsers HTML where they asked for a script.
        given().when().get("/assets/index-stale-hash.js").then().statusCode(404);
        given().when().get("/humans.txt").then().statusCode(404);
        given().when().get("/.well-known/security.txt").then().statusCode(404);
    }

    @Test
    void unknownExtensionlessPathsStillGetTheSpaShell() {
        // Client-side routes must survive a full-page reload.
        given()
                .when().get("/findings")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"));
    }

    @Test
    void noCanonicalRedirectIsAppliedWhenTheHostIsUnconfigured() {
        // Default config leaves findatex.web.canonical-host empty — self-hosted
        // instances answer on whatever hostname they are deployed under.
        given().redirects().follow(false)
                .when().get("/")
                .then()
                .statusCode(200);
    }
}
