package com.tpt.validator.spec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for the quoted-subcategory qualifier parsing in {@link SpecLoader#parseSubcategoryQualifier}. */
class SubcategoryQualifierTest {

    @Test
    void corporateBondQualifierExtracts22And29() {
        Set<String> subs = SpecLoader.parseSubcategoryQualifier(
                "x for convertible bonds \"22\" or other corporate bonds \"29\" quoted in units",
                "CIC2");
        assertThat(subs).containsExactlyInAnyOrder("2", "9");
    }

    @Test
    void futureQualifierExtractsA1A5A9() {
        Set<String> subs = SpecLoader.parseSubcategoryQualifier(
                "x for equity future \"A1\" and for commodity future \"A5\", other \"A9\"",
                "CICA");
        assertThat(subs).containsExactlyInAnyOrder("1", "5", "9");
    }

    @Test
    void optionQualifierExtractsB1B4B5B9() {
        Set<String> subs = SpecLoader.parseSubcategoryQualifier(
                "x for equity options \"B1\", warrants \"B4\", commodities options \"B5\", others \"B9\"",
                "CICB");
        assertThat(subs).containsExactlyInAnyOrder("1", "4", "5", "9");
    }

    @Test
    void plainXProducesNoRestriction() {
        Set<String> subs = SpecLoader.parseSubcategoryQualifier("x", "CIC2");
        assertThat(subs).isEmpty();
    }

    @Test
    void blankInputProducesNoRestriction() {
        assertThat(SpecLoader.parseSubcategoryQualifier(null, "CIC2")).isEmpty();
        assertThat(SpecLoader.parseSubcategoryQualifier("", "CIC2")).isEmpty();
    }

    @Test
    void tokensFromOtherCicClassesAreIgnored() {
        // Hypothetical mixed text: only the prefix matching the CIC class wins.
        Set<String> subs = SpecLoader.parseSubcategoryQualifier(
                "x for \"22\", \"A1\", \"B4\"", "CIC2");
        assertThat(subs).containsExactly("2");
    }

    @Test
    void unquotedTokensAfterForKeywordArePicked() {
        // The original spec uses both quoted and unquoted styles. The unquoted style is
        // dominant (e.g. 'x for 22', 'x for D4, D5', 'x for 73, 74, 75').
        assertThat(SpecLoader.parseSubcategoryQualifier("x for 22 and 29", "CIC2"))
                .containsExactlyInAnyOrder("2", "9");
        assertThat(SpecLoader.parseSubcategoryQualifier("x for D4, D5", "CICD"))
                .containsExactlyInAnyOrder("4", "5");
    }

    @Test
    void unquotedTokensAreOnlyPickedAfterAStandaloneForKeyword() {
        // No 'for' keyword → no unquoted extraction (cross-field 'if item X is "1"' clauses
        // must not contribute false positives even when they contain 2-char digit pairs).
        assertThat(SpecLoader.parseSubcategoryQualifier("x if item 22 is set", "CIC2")).isEmpty();
        assertThat(SpecLoader.parseSubcategoryQualifier("if item 42 is Equal to Cal or Put", "CIC1"))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "CIC2,  '\"21\"',  1",
            "CIC2,  '\"21\" \"22\" \"29\"', 1",
            "CICA,  '\"A1\" \"A5\"', 1",
    })
    void firstTokenStrippedToSecondChar(String cic, String text, String firstSub) {
        Set<String> subs = SpecLoader.parseSubcategoryQualifier(text, cic);
        assertThat(subs).contains(firstSub);
    }
}
