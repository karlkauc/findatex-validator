package com.findatex.validator.web.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The parser reads a <b>generated</b> document, so its contract is with
 * {@code RuleDocGenerator}'s output shape rather than with hand-written text.
 * If that shape moves, the public pages would silently render empty; these
 * assertions make it fail loudly instead.
 */
@QuarkusTest
class RuleDocsTest {

    @Inject
    RuleDocs docs;

    @Test
    void everyBundledTemplateVersionIsIndexed() {
        assertThat(docs.index()).hasSize(8);
        assertThat(docs.index()).extracting(RuleDocs.DocRef::slug)
                .contains("tpt-v8-0", "tpt-v7-0", "eet-v1-1-3", "emt-v4-3", "ept-v2-1");
        // Newest first per template — the sitemap relies on this to tell a
        // current version from a superseded one.
        assertThat(docs.index().get(0).slug()).isEqualTo("tpt-v8-0");
    }

    @Test
    void theIntroStopsBeforeThePerFieldCatalog() {
        RuleDocs.Doc doc = docs.doc("tpt-v8-0").orElseThrow();

        assertThat(doc.intro())
                .contains("How this validator scores your file")
                .contains("Cross-field rules")
                .doesNotContain("## 5. Per-field catalog")
                .doesNotContain("### Field 1 —");
    }

    @Test
    void fieldsAreSplitOutIncludingSuffixedNumbers() {
        RuleDocs.Doc doc = docs.doc("tpt-v8-0").orElseThrow();

        assertThat(doc.fields()).hasSizeGreaterThan(100);
        // TPT numbers fields like "8b"; a numeric-only parse would drop them.
        assertThat(doc.fields()).containsKey("8b");
        assertThat(doc.fields()).containsKey("1");
    }

    @Test
    void aFieldCarriesItsName_definitionAndTables() {
        RuleDocs.Field field = docs.doc("tpt-v8-0").orElseThrow().field("26").orElseThrow();

        assertThat(field.name()).isEqualTo("26_Valuation_weight");
        assertThat(field.definition()).isNotBlank();
        assertThat(field.markdown())
                .contains("Codification:")
                .contains("| Rule ID |")
                // The entry's own heading is rendered as the page h1 instead.
                .doesNotStartWith("### Field")
                // h4 lifted to h2 so the page has no gap in its heading levels.
                .contains("## Flag per profile")
                .doesNotContain("#### ")
                // The generator's separator between entries must not leak in.
                .doesNotEndWith("---");
    }

    @Test
    void unknownSlugsAndFieldsResolveToEmptyRatherThanThrowing() {
        assertThat(docs.doc("nope")).isEmpty();
        assertThat(docs.ref("nope")).isEmpty();
        assertThat(docs.doc("tpt-v8-0").orElseThrow().field("9999")).isEmpty();
    }

    @Test
    void everyIndexedDocumentParsesIntoFields() {
        for (RuleDocs.DocRef ref : docs.index()) {
            RuleDocs.Doc doc = docs.doc(ref.slug()).orElseThrow();
            assertThat(doc.intro()).as("intro of " + ref.slug()).isNotBlank();
            assertThat(doc.fields()).as("fields of " + ref.slug()).isNotEmpty();
        }
    }
}
