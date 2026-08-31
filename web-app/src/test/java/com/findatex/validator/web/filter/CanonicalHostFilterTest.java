package com.findatex.validator.web.filter;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * Canonical-host redirect with a canonical host the test client never uses:
 * RestAssured talks to {@code localhost}, so every request here arrives on a
 * "foreign" hostname and must be 301'd.
 *
 * <p>The matching-host case (no redirect) lives in
 * {@link CanonicalHostMatchTest}, which needs a second profile.
 */
@QuarkusTest
@TestProfile(CanonicalHostFilterTest.ForeignHost.class)
class CanonicalHostFilterTest {

    @Test
    void getOnAForeignHostIsRedirectedPermanentlyToTheCanonicalOne() {
        given().redirects().follow(false)
                .when().get("/")
                .then()
                .statusCode(301)
                .header("Location", equalTo("https://canonical.test/"));
    }

    @Test
    void pathAndQueryStringSurviveTheRedirect() {
        given().redirects().follow(false)
                .when().get("/findings?template=TPT&v=V8.0")
                .then()
                .statusCode(301)
                .header("Location", equalTo("https://canonical.test/findings?template=TPT&v=V8.0"));
    }

    @Test
    void staticAssetsAreRedirectedToo() {
        // The filter sits in front of the static-resource handler — that is the
        // point, since "/" itself never reaches the JAX-RS layer.
        given().redirects().follow(false)
                .when().get("/robots.txt")
                .then()
                .statusCode(301)
                .header("Location", equalTo("https://canonical.test/robots.txt"));
    }

    @Test
    void postIsNeverRedirected() {
        // Browsers turn a 301'd POST into a GET, which would silently drop an
        // upload. Uploads must fail loudly or succeed, never half-redirect.
        given().redirects().follow(false)
                .multiPart("templateId", "TPT")
                .when().post("/api/validate")
                .then()
                .statusCode(not(equalTo(301)));
    }

    @Test
    void healthProbesAreNeverRedirected() {
        // Probes reach the service on its internal hostname; bouncing them there
        // would make the deployment look unhealthy.
        given().redirects().follow(false)
                .when().get("/_internal/health")
                .then()
                .statusCode(200);
    }

    public static final class ForeignHost implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("findatex.web.canonical-host", "canonical.test");
        }
    }
}
