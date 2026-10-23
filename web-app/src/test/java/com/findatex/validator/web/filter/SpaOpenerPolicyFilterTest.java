package com.findatex.validator.web.filter;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * The shell must keep {@code window.opener} for the hand-over from the TPT
 * converter; every other response keeps the strict opener policy.
 */
@QuarkusTest
class SpaOpenerPolicyFilterTest {

    private static final String COOP = "Cross-Origin-Opener-Policy";

    @Test
    void theShellAllowsTheOpenerToStay() {
        given().when().get("/?from=tpt-converter")
                .then().statusCode(200)
                .header(COOP, "unsafe-none");
        given().when().get("/index.html?from=tpt-converter")
                .then().statusCode(200)
                .header(COOP, "unsafe-none");
    }

    @Test
    void aPlainVisitKeepsSameOrigin() {
        given().when().get("/")
                .then().statusCode(200)
                .header(COOP, "same-origin");
        given().when().get("/?from=somewhere-else")
                .then().statusCode(200)
                .header(COOP, "same-origin");
    }

    @Test
    void everythingElseKeepsSameOrigin() {
        given().when().get("/api/templates")
                .then().statusCode(200)
                .header(COOP, "same-origin");
        given().when().get("/help")
                .then().statusCode(200)
                .header(COOP, "same-origin");
    }
}
