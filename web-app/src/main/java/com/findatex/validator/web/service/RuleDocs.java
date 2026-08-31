package com.findatex.validator.web.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the generated rule reference ({@code docs/rules/*.md}, bundled onto the
 * classpath under {@code help/rules/}) and splits each document into the parts
 * the public pages are built from.
 *
 * <p>Until now this content existed only behind {@code GET /api/help/rules/…},
 * rendered inside a modal — roughly 57k lines of specific, hard-to-find
 * material that no search engine could see. The split here is what turns it
 * into pages: the intro (scoring, profiles, general and cross-field rules) is
 * one substantial page per template version, and each per-field entry becomes
 * its own page, because "TPT field 117" is the shape of the query people
 * actually type.
 *
 * <p>Parsing is deliberately structural rather than clever: the generator emits
 * a fixed shape ({@code ## 5. Per-field catalog}, then {@code ### Field N —
 * name}), and {@code RuleDocsTest} fails if that shape changes rather than
 * letting the pages silently go empty.
 */
@ApplicationScoped
public class RuleDocs {

    private static final Logger log = LoggerFactory.getLogger(RuleDocs.class);

    private static final String INDEX_RESOURCE = "help/rules/index.json";
    private static final String DOC_RESOURCE = "help/rules/%s.md";

    /** Start of the per-field catalog; everything before it is the intro. */
    private static final String CATALOG_HEADING = "## 5. Per-field catalog";

    /** {@code ### Field 8b — 8b_Total_number_of_shares} */
    private static final Pattern FIELD_HEADING =
            Pattern.compile("^### Field (\\S+) — (.*)$");

    /** {@code Definition: …} — the one line worth using as a meta description. */
    private static final Pattern DEFINITION = Pattern.compile("^Definition: (.+)$",
            Pattern.MULTILINE);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<DocRef> index = List.of();

    /** Parsed documents are a few MB in total; a handful of entries is plenty. */
    private final Cache<String, Optional<Doc>> cache = Caffeine.newBuilder()
            .maximumSize(16)
            .build();

    @PostConstruct
    void init() {
        try (InputStream in = resource(INDEX_RESOURCE)) {
            if (in == null) {
                log.warn("Rule reference index not on the classpath — /rules pages will be empty");
                return;
            }
            index = List.of(MAPPER.readValue(in.readAllBytes(), DocRef[].class));
        } catch (IOException e) {
            log.warn("Rule reference index unreadable ({}) — /rules pages will be empty", e.toString());
        }
    }

    /** Every documented template version, in the generator's order. */
    public List<DocRef> index() {
        return index;
    }

    public Optional<DocRef> ref(String slug) {
        return index.stream().filter(r -> r.slug().equals(slug)).findFirst();
    }

    /** Parsed document for a slug, or empty when the slug is unknown. */
    public Optional<Doc> doc(String slug) {
        if (slug == null || ref(slug).isEmpty()) return Optional.empty();
        return cache.get(slug, this::parse);
    }

    private Optional<Doc> parse(String slug) {
        String markdown;
        try (InputStream in = resource(DOC_RESOURCE.formatted(slug))) {
            if (in == null) return Optional.empty();
            markdown = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Rule doc '{}' unreadable ({})", slug, e.toString());
            return Optional.empty();
        }

        int catalogAt = markdown.indexOf(CATALOG_HEADING);
        String intro = catalogAt < 0 ? markdown : markdown.substring(0, catalogAt);
        String catalog = catalogAt < 0 ? "" : markdown.substring(catalogAt);

        return Optional.of(new Doc(slug, intro.strip(), parseFields(catalog)));
    }

    /** Splits the catalog on its {@code ### Field …} headings, in document order. */
    private static Map<String, Field> parseFields(String catalog) {
        Map<String, Field> fields = new LinkedHashMap<>();
        String[] lines = catalog.split("\n", -1);

        String num = null;
        String name = null;
        List<String> body = new ArrayList<>();
        for (String line : lines) {
            Matcher m = FIELD_HEADING.matcher(line);
            if (m.matches()) {
                if (num != null) fields.put(num, field(num, name, body));
                num = m.group(1);
                name = m.group(2).strip();
                body = new ArrayList<>();
            } else if (num != null) {
                body.add(line);
            }
        }
        if (num != null) fields.put(num, field(num, name, body));
        return fields;
    }

    private static Field field(String num, String name, List<String> body) {
        String markdown = String.join("\n", body).strip();
        // Drop the trailing "---" the generator puts between entries, and lift
        // the entry's h4 subheadings to h2: the page renders the field name as
        // its h1, so h4 under h1 would be a hole in the heading hierarchy.
        while (markdown.endsWith("---")) {
            markdown = markdown.substring(0, markdown.length() - 3).strip();
        }
        markdown = markdown.replaceAll("(?m)^#### ", "## ");

        Matcher d = DEFINITION.matcher(markdown);
        String definition = d.find() ? d.group(1).strip() : null;
        return new Field(num, name, markdown, definition);
    }

    private static InputStream resource(String path) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    }

    /** One entry of {@code help/rules/index.json}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocRef(String slug, String templateId, String templateDisplayName,
                         String version, String label) {
    }

    /** A parsed reference document: the intro plus its per-field entries. */
    public record Doc(String slug, String intro, Map<String, Field> fields) {

        public Optional<Field> field(String num) {
            return Optional.ofNullable(fields.get(num));
        }
    }

    /**
     * One field entry. {@code definition} is the spec's own wording, used as
     * the page's meta description — a hand-written one per field is not
     * maintainable at 2000 pages, and the spec text is what a searcher is
     * looking for anyway.
     */
    public record Field(String num, String name, String markdown, String definition) {
    }
}
