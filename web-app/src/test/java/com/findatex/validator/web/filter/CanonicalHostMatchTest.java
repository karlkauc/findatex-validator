package com.findatex.validator.web.filter;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Counterpart to {@link CanonicalHostFilterTest}: when the request already
 * arrives on the canonical host, nothing happens. Configured as {@code
 * localhost} because that is the hostname RestAssured connects to — a redirect
 * loop would show up here as a 301 instead of the served page.
 */
@QuarkusTest
@TestProfile(CanonicalHostMatchTest.MatchingHost.class)
class CanonicalHostMatchTest {

    @Test
    void requestsOnTheCanonicalHostAreServedDirectly() {
        given().redirects().follow(false)
                .when().get("/robots.txt")
                .then()
                .statusCode(200);
    }

    @Test
    void theHostComparisonIgnoresThePort() {
        // The test server runs on a random-ish port; "localhost:8081" must still
        // match the configured "localhost", or every request would loop.
        given().redirects().follow(false)
                .when().get("/api/templates")
                .then()
                .statusCode(200);
    }

    @Test
    void theGeneratedSitemapUsesTheCanonicalHostNotTheRequestHost() {
        // The request arrives on localhost:<random port>; a sitemap that echoed
        // that back would list URLs nobody can crawl.
        given().redirects().follow(false)
                .when().get("/sitemap.xml")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.containsString("<loc>https://localhost/</loc>"));
    }

    public static final class MatchingHost implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("findatex.web.canonical-host", "localhost");
        }
    }
}
