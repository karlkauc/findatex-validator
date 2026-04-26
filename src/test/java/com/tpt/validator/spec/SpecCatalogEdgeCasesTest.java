package com.tpt.validator.spec;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpecCatalogEdgeCasesTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    @Test
    void byNumKeyForUnknownReturnsEmpty() {
        assertThat(CATALOG.byNumKey("9999")).isEmpty();
        assertThat(CATALOG.byNumKey("")).isEmpty();
    }

    @Test
    void matchHeaderHandlesNumKeyAlone() {
        assertThat(CATALOG.matchHeader("12")).isPresent();
        assertThat(CATALOG.matchHeader("8b")).isPresent();
        assertThat(CATALOG.matchHeader("1000")).isPresent();
    }

    @Test
    void matchHeaderHandlesPrefixedNumData() {
        assertThat(CATALOG.matchHeader("12_CIC_code_of_the_instrument")).isPresent();
        assertThat(CATALOG.matchHeader("8b_Total_number_of_shares")).isPresent();
    }

    @Test
    void matchHeaderHandlesFunDataXmlPath() {
        Optional<FieldSpec> m = CATALOG.matchHeader("Position / InstrumentCIC");
        assertThat(m).isPresent();
        assertThat(m.get().numKey()).isEqualTo("12");
    }

    @Test
    void matchHeaderTreatsPathWithMixedCaseAndWhitespaceAsEqual() {
        // The Catalog's internal path normaliser strips spaces and lowercases.
        assertThat(CATALOG.matchHeader("position/InstrumentCIC")).isPresent();
        assertThat(CATALOG.matchHeader("  Position / InstrumentCIC  ")).isPresent();
    }

    @Test
    void matchHeaderRejectsRubbish() {
        assertThat(CATALOG.matchHeader("foo bar baz")).isEmpty();
        assertThat(CATALOG.matchHeader(null)).isEmpty();
        assertThat(CATALOG.matchHeader("")).isEmpty();
        assertThat(CATALOG.matchHeader("    ")).isEmpty();
    }

    @Test
    void manuallyConstructedCatalogPreservesInsertionOrder() {
        FieldSpec a = stub("12", "Position / A");
        FieldSpec b = stub("17", "Position / B");
        SpecCatalog c = new SpecCatalog(List.of(a, b));
        assertThat(c.fields()).containsExactly(a, b);
        assertThat(c.byNumKey("12")).contains(a);
        assertThat(c.byNumKey("17")).contains(b);
    }

    @Test
    void duplicateNumKeysKeepFirstWin() {
        FieldSpec first  = stub("12", "Position / A");
        FieldSpec second = stub("12", "Position / B");
        SpecCatalog c = new SpecCatalog(List.of(first, second));
        // putIfAbsent semantics — first wins
        assertThat(c.byNumKey("12")).contains(first);
    }

    @Test
    void catalogWithEmptyPathSkipsPathLookup() {
        FieldSpec spec = stub("99", "");
        SpecCatalog c = new SpecCatalog(List.of(spec));
        assertThat(c.byNumKey("99")).contains(spec);
        assertThat(c.byPath("")).isEmpty();
    }

    private static FieldSpec stub(String numKey, String path) {
        return new FieldSpec(numKey + "_x", path, "def", "comment", "raw",
                new CodificationDescriptor(CodificationKind.UNKNOWN, Optional.empty(), List.of(), "raw"),
                new EnumMap<>(Profile.class), Set.of(), 1);
    }
}
