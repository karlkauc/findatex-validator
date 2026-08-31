package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Endpoint contract for the page-view beacon. Tests run without a stats DB, so
 * this is the inert path — which is exactly the one that must not misbehave:
 * a self-hosted instance with no database still serves this endpoint on every
 * page load.
 *
 * <p>The invariant under test is "always 204, never anything else". The
 * sanitising and bot-filtering logic is covered offline by
 * {@code PageViewServiceTest} and {@code BotDetectorTest}.
 */
@QuarkusTest
class PageViewResourceTest {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/141.0.0.0 Safari/537.36";

    @Test
    void acceptsABeaconWithNoContent() {
        given()
                .header("User-Agent", BROWSER_UA)
                .contentType("application/json")
                .body("{\"path\":\"/\",\"referrer\":\"https://www.linkedin.com/feed/\",\"campaign\":\"linkedin\"}")
                .when().post("/api/page-view")
                .then().statusCode(204);
    }

    @Test
    void anEmptyOrPartialBodyIsStillAccepted() {
        // Every field is optional: a beacon from a page with no referrer and no
        // campaign is the normal case, not an error.
        given().header("User-Agent", BROWSER_UA)
                .contentType("application/json").body("{}")
                .when().post("/api/page-view")
                .then().statusCode(204);

        given().header("User-Agent", BROWSER_UA)
                .contentType("application/json").body("{\"path\":\"/\"}")
                .when().post("/api/page-view")
                .then().statusCode(204);
    }

    @Test
    void unknownFieldsDoNotBreakTheBeacon() {
        // An older container must keep accepting beacons from a newer SPA build.
        given().header("User-Agent", BROWSER_UA)
                .contentType("application/json")
                .body("{\"path\":\"/\",\"somethingNew\":42}")
                .when().post("/api/page-view")
                .then().statusCode(204);
    }

    @Test
    void aBotBeaconIsAcceptedAndDiscarded() {
        // Answering differently would tell a probing client whether it was
        // classified as a bot.
        given().header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1)")
                .contentType("application/json").body("{\"path\":\"/\"}")
                .when().post("/api/page-view")
                .then().statusCode(204);
    }
}
