package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
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
    void sitemapIsServedAsXmlListingTheCanonicalHomepage() {
        given()
                .when().get("/sitemap.xml")
                .then()
                .statusCode(200)
                .contentType(containsString("xml"))
                .body(containsString("<loc>https://www.findatex-validator.eu/</loc>"));
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
