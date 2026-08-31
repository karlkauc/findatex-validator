package com.findatex.validator.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the SEO metadata internally consistent. Four files have to agree on the
 * same facts, and nothing at runtime notices when they stop agreeing — a wrong
 * canonical host or a stale CSP hash fails silently, in the crawler, weeks later.
 *
 * <p>Plain JUnit on the source files (no Quarkus boot): what matters is what is
 * committed, and the frontend sources are what Vite ships verbatim.
 */
class SeoMetadataTest {

    private static final Path INDEX_HTML = Path.of("src/main/frontend/index.html");
    private static final Path OG_IMAGE = Path.of("src/main/frontend/public/og-image.png");
    private static final Path ROBOTS_TXT = Path.of("src/main/resources/META-INF/resources/robots.txt");
    private static final Path SITEMAP_XML = Path.of("src/main/resources/META-INF/resources/sitemap.xml");
    private static final Path PROPERTIES = Path.of("src/main/resources/application.properties");

    private static final String CANONICAL_HOST = "www.findatex-validator.eu";

    @Test
    void indexHtmlCarriesTheTagsSearchEnginesAndLinkPreviewsRead() throws IOException {
        String html = read(INDEX_HTML);

        assertThat(html).contains("<title>FinDatEx Validator");
        assertThat(html).containsPattern("<meta\\s+name=\"description\"");
        assertThat(html).contains("<link rel=\"canonical\" href=\"https://" + CANONICAL_HOST + "/\" />");

        assertThat(html).contains("property=\"og:type\"");
        assertThat(html).contains("property=\"og:title\"");
        assertThat(html).contains("property=\"og:description\"");
        assertThat(html).contains("property=\"og:url\"");
        assertThat(html).contains("property=\"og:image\"");
        assertThat(html).contains("name=\"twitter:card\"");
    }

    @Test
    void theOpenGraphImageExistsAndIsServedFromTheCanonicalHost() throws IOException {
        String html = read(INDEX_HTML);
        assertThat(html).contains("https://" + CANONICAL_HOST + "/og-image.png");
        // 1200x630 is what every link-preview renderer crops to; the file has to
        // actually ship, otherwise the preview falls back to a bare text card.
        assertThat(OG_IMAGE).exists();
        assertThat(html).contains("<meta property=\"og:image:width\" content=\"1200\" />");
        assertThat(html).contains("<meta property=\"og:image:height\" content=\"630\" />");
    }

    @Test
    void theInlineJsonLdIsValidJsonAndDescribesTheApplication() throws IOException {
        JsonNode ld = new ObjectMapper().readTree(jsonLdBlock(read(INDEX_HTML)));

        assertThat(ld.path("@type").asText()).isEqualTo("SoftwareApplication");
        assertThat(ld.path("name").asText()).isEqualTo("FinDatEx Validator");
        assertThat(ld.path("url").asText()).isEqualTo("https://" + CANONICAL_HOST + "/");
    }

    @Test
    void theCspAllowListsExactlyTheInlineJsonLdThatIsShipped() throws IOException {
        String expected = "'sha256-" + sha256Base64(jsonLdBlock(read(INDEX_HTML))) + "'";

        // If this fails, the JSON-LD block was edited without updating the hash:
        // browsers then drop the structured data with a CSP violation. Copy the
        // expected value from the failure message into script-src.
        assertThat(read(PROPERTIES))
                .as("script-src hash in application.properties must match index.html's JSON-LD")
                .contains(expected);
    }

    @Test
    void robotsAndSitemapPointAtTheSameCanonicalHostAsIndexHtml() throws IOException {
        assertThat(read(ROBOTS_TXT))
                .contains("Sitemap: https://" + CANONICAL_HOST + "/sitemap.xml");
        assertThat(read(SITEMAP_XML))
                .contains("<loc>https://" + CANONICAL_HOST + "/</loc>");
    }

    @Test
    void robotsKeepsCrawlersOutOfTheApiAndHealthNamespaces() throws IOException {
        String robots = read(ROBOTS_TXT);
        assertThat(robots).contains("Disallow: /api/");
        assertThat(robots).contains("Disallow: /_internal/");
    }

    private static String jsonLdBlock(String html) {
        Matcher m = Pattern.compile("<script type=\"application/ld\\+json\">(.*?)</script>",
                Pattern.DOTALL).matcher(html);
        assertThat(m.find()).as("index.html must contain a JSON-LD block").isTrue();
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

    private static String read(Path path) throws IOException {
        assertThat(path).as("expected to run from the web-app module dir").exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
