package com.findatex.validator.web.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

/** Imprint and privacy policy: server-rendered, linked from every page, not indexed. */
@QuarkusTest
class LegalPagesTest {

    @Test
    void theImprintNamesTheMediaOwner() {
        assertLegalPage("/imprint", "Media owner");
    }

    @Test
    void thePrivacyPolicyNamesTheController() {
        assertLegalPage("/privacy", "Who is responsible");
    }

    @Test
    void theStaticPagesLinkBoth() {
        given().when().get("/help").then().statusCode(200)
                .body(containsString("href=\"/imprint\""))
                .body(containsString("href=\"/privacy\""))
                .body(not(containsString("<meta name=\"robots\" content=\"noindex\">")));
    }

    @Test
    void neitherPageIsInTheSitemap() {
        given().when().get("/sitemap.xml").then().statusCode(200)
                .body(not(containsString("/imprint")))
                .body(not(containsString("/privacy")));
    }

    private static void assertLegalPage(String path, String heading) {
        given().when().get(path).then().statusCode(200)
                .contentType(startsWith("text/html"))
                .body(containsString("<h2>" + heading + "</h2>"))
                .body(containsString("<meta name=\"robots\" content=\"noindex\">"))
                .body(containsString("<link rel=\"canonical\" href=\"http"))
                .body(not(containsString("<div id=\"root\">")))
                .body(not(containsString("class=\"cta-box\"")));
    }
}
