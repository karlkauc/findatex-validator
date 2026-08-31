package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

/**
 * The public rule pages. What matters is that they are <b>readable without
 * JavaScript</b> — the content is the whole reason they exist — and that each
 * one carries its own title, description and canonical, since a page that
 * inherits the SPA's metadata is invisible in a result list.
 */
@QuarkusTest
class RulesPageResourceTest {

    @Test
    void theIndexListsEveryTemplateVersion() {
        given()
                .when().get("/rules")
                .then()
                .statusCode(200)
                .contentType(startsWith("text/html"))
                .body(containsString("<title>FinDatEx validation rules"))
                .body(containsString("/rules/tpt-v8-0"))
                .body(containsString("/rules/eet-v1-1-3"))
                .body(containsString("/rules/ept-v2-0"));
    }

    @Test
    void aTemplateVersionPageRendersItsRulesAndLinksEveryField() {
        given()
                .when().get("/rules/tpt-v8-0")
                .then()
                .statusCode(200)
                .body(containsString("<title>TPT V8.0 validation rules"))
                .body(containsString("<link rel=\"canonical\" href=\"http"))
                // Content, not a shell to be filled in by a bundle.
                .body(containsString("Cross-field rules"))
                .body(containsString("XF-04/POSITION_WEIGHT_SUM"))
                .body(containsString("/rules/tpt-v8-0/field/26"))
                // Markdown tables must survive as tables.
                .body(containsString("<table>"));
    }

    @Test
    void aFieldPageCarriesItsOwnTitleDescriptionAndContent() {
        given()
                .when().get("/rules/tpt-v8-0/field/26")
                .then()
                .statusCode(200)
                .body(containsString("<title>TPT V8.0 field 26 — 26_Valuation_weight"))
                // The description is the spec's own definition of the field.
                .body(containsString("<meta name=\"description\" content=\"Market valuation"))
                .body(containsString("<link rel=\"canonical\" href=\"http"))
                .body(containsString("PRESENCE/26/SOLVENCY_II"))
                .body(containsString("Flag per profile"))
                // Its own h1, not the generator's "### Field 26" heading.
                .body(containsString("<h1>TPT V8.0 field 26</h1>"));
    }

    @Test
    void fieldNumbersWithALetterSuffixResolve() {
        given().when().get("/rules/tpt-v8-0/field/8b")
                .then().statusCode(200)
                .body(containsString("8b"));
    }

    @Test
    void everyPageLinksBackIntoTheValidator() {
        // These pages exist to turn a search result into a user; without the
        // call to action they are just documentation someone else hosts.
        given().when().get("/rules/tpt-v8-0/field/26")
                .then().statusCode(200)
                .body(containsString("Open the validator"))
                .body(containsString("href=\"/\""));
    }

    @Test
    void unknownSlugsAndFieldsAre404NotTheSpaShell() {
        // SpaFallbackResource must not swallow these: a soft 200 would tell a
        // crawler that every made-up /rules/... URL is a real page.
        given().when().get("/rules/does-not-exist")
                .then().statusCode(404)
                .body(not(containsString("<div id=\"root\">")));
        given().when().get("/rules/tpt-v8-0/field/9999").then().statusCode(404);
    }

    @Test
    void pagesAreCacheableAndCarryTheBeaconScript() {
        given().when().get("/rules")
                .then().statusCode(200)
                .header("Cache-Control", containsString("max-age"))
                // Page views on these pages are what will show whether the rule
                // reference actually pulls traffic.
                .body(containsString("/rules-page.js"));
    }
}
