package com.findatex.validator.web.filter;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * Only {@code /addin/*} may be framed by Office and load office.js; every other
 * page keeps {@code X-Frame-Options: DENY} and {@code frame-ancestors 'none'}.
 */
@QuarkusTest
class AddinFramingFilterTest {

    private static final String CSP = "Content-Security-Policy";

    @Test
    void theManifestIsServedFramableAndUncached() {
        given().when().get("/addin/manifest.xml")
                .then().statusCode(200)
                .contentType(containsString("xml"))
                .body(containsString("<OfficeApp"))
                .body(containsString("https://www.findatex-validator.eu/addin/taskpane.html"))
                .header("X-Frame-Options", nullValue())
                .header(CSP, containsString("frame-ancestors 'self' https://*.officeapps.live.com"))
                .header(CSP, containsString("script-src 'self' https://appsforoffice.microsoft.com;"))
                .header(CSP, not(containsString("frame-ancestors 'none'")))
                .header("Cache-Control", "no-cache");
    }

    @Test
    void addinIconsAreServed() {
        given().when().get("/addin/icon-32.png")
                .then().statusCode(200)
                .contentType(startsWith("image/png"))
                .header("X-Frame-Options", nullValue());
    }

    @Test
    void everythingElseStaysUnframable() {
        for (String path : new String[] {"/", "/help", "/rules", "/api/templates"}) {
            given().when().get(path)
                    .then().statusCode(200)
                    .header("X-Frame-Options", "DENY")
                    .header(CSP, containsString("frame-ancestors 'none'"))
                    .header(CSP, not(containsString("appsforoffice")));
        }
    }

    @Test
    void onlyTheAddinPrefixMatches() {
        org.assertj.core.api.Assertions.assertThat(AddinFramingFilter.isAddinPath("/addin/taskpane.html")).isTrue();
        org.assertj.core.api.Assertions.assertThat(AddinFramingFilter.isAddinPath("/addin")).isFalse();
        org.assertj.core.api.Assertions.assertThat(AddinFramingFilter.isAddinPath("/addins/x")).isFalse();
        org.assertj.core.api.Assertions.assertThat(AddinFramingFilter.isAddinPath("/api/addin/x")).isFalse();
    }
}
