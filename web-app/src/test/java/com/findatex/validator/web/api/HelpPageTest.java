package com.findatex.validator.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

/**
 * The public help page: {@code /help} renders the bundled {@code HELP.md} as a
 * crawlable, server-rendered page. It carries the landing copy that used to be
 * static HTML in {@code index.html}, so the checks that used to run against
 * that file — FAQ answers visible on the page, latest spec versions named —
 * now run against the served page.
 *
 * <p>The structured-data check reads the <b>response</b> header, not the
 * properties file: what matters is the CSP the browser actually receives for
 * the block it actually receives.
 */
@QuarkusTest
class HelpPageTest {

    @Test
    void theHelpPageIsServerRenderedHtmlWithItsOwnMetadata() {
        given()
                .when().get("/help")
                .then()
                .statusCode(200)
                .contentType(startsWith("text/html"))
                .header("Cache-Control", containsString("max-age"))
                .body(containsString("<title>FinDatEx Validator — Help &amp; FAQ"))
                .body(containsString("<meta name=\"description\" content=\""))
                .body(containsString("<link rel=\"canonical\" href=\"http"))
                .body(containsString("/help\">"))
                // Real content, rendered from the Markdown: headings and a table.
                .body(containsString("<h2>"))
                .body(containsString("<table>"))
                // Not the SPA shell.
                .body(not(containsString("/assets/index-")))
                .body(not(containsString("<div id=\"root\">")));
    }

    @Test
    void theHelpPageLinksTheRuleReference() {
        given().when().get("/help")
                .then().statusCode(200)
                .body(containsString("/rules\""));
    }

    @Test
    void everyFaqAnswerInTheMarkupIsAlsoVisibleOnThePage() throws IOException {
        // Google requires FAQPage answers to be present in the rendered page;
        // markup-only answers are a structured-data violation, not a shortcut.
        String html = page();
        JsonNode faq = faqNode(html);
        String body = visibleText(html);

        JsonNode questions = faq.path("mainEntity");
        assertThat(questions).as("FAQ entries").isNotEmpty();
        for (JsonNode q : questions) {
            assertThat(body)
                    .as("question rendered on the page: " + q.path("name").asText())
                    .contains(q.path("name").asText());
            assertThat(body)
                    .as("answer rendered on the page for: " + q.path("name").asText())
                    .contains(q.path("acceptedAnswer").path("text").asText());
        }
    }

    @Test
    void thePageNamesTheCurrentSpecVersionOfEveryTemplate() {
        // A new template version that nobody mentions in the help is a silent
        // content regression: the page keeps advertising the superseded one.
        TemplateRegistry.init();
        String body = visibleText(page());

        for (TemplateDefinition def : TemplateRegistry.all()) {
            assertThat(body)
                    .as("latest %s version named on /help", def.id())
                    .contains(def.latest().version());
        }
    }

    @Test
    void theCspHeaderAllowListsTheInlineJsonLdThatIsServed() {
        Response response = given().when().get("/help");
        response.then().statusCode(200);

        String expected = "'sha256-" + sha256Base64(jsonLdBlock(response.body().asString())) + "'";

        // If this fails, HelpPageResource.FAQ_JSON_LD was edited without updating
        // the hash: browsers then drop the structured data with a CSP violation.
        // Copy the expected value from the failure message into script-src.
        assertThat(response.header("Content-Security-Policy"))
                .as("script-src hash in the CSP response header must match the served JSON-LD")
                .contains(expected);
    }

    private static String page() {
        return given().when().get("/help").then().statusCode(200).extract().body().asString();
    }

    private static JsonNode faqNode(String html) throws IOException {
        JsonNode ld = new ObjectMapper().readTree(jsonLdBlock(html));
        if ("FAQPage".equals(ld.path("@type").asText())) return ld;
        for (JsonNode node : ld.path("@graph")) {
            if ("FAQPage".equals(node.path("@type").asText())) return node;
        }
        throw new AssertionError("No FAQPage node in the JSON-LD block");
    }

    /**
     * Body text with tags, entities and the head stripped — an approximation of
     * what a reader (and a crawler that does not run JavaScript) sees.
     */
    private static String visibleText(String html) {
        String body = html.substring(html.indexOf("<body"));
        String text = body.replaceAll("(?s)<!--.*?-->", " ")
                .replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"");
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String jsonLdBlock(String html) {
        Matcher m = Pattern.compile("<script type=\"application/ld\\+json\">(.*?)</script>",
                Pattern.DOTALL).matcher(html);
        assertThat(m.find()).as("/help must contain a JSON-LD block").isTrue();
        return m.group(1);
    }

    private static String sha256Base64(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
