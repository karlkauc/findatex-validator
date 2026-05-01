package com.findatex.validator.spec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CodificationParserCornerCasesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void blankCodificationIsUnknown(String raw) {
        CodificationDescriptor d = CodificationParser.parse(raw);
        assertThat(d.kind()).isEqualTo(CodificationKind.UNKNOWN);
        assertThat(d.closedList()).isEmpty();
        assertThat(d.maxLength()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Code ISO 4217                         | ISO_4217",
            "Code ISO 3166-1 alpha 2               | ISO_3166_A2",
            "YYYY-MM-DD ISO 8601                   | DATE",
            "YYYY-MM-DD                            | DATE",
            "NACE V2.1 Code                        | NACE",
            "CIC code - Alphanumeric (4)           | CIC",
            "Alphanumeric (4)                      | CIC",
            "number with floating decimal          | NUMERIC",
            "Alpha(1)                              | ALPHA",
            "Alpha (3)                             | ALPHA",
            "Alphanum (max 255)                    | ALPHANUMERIC",
            "Alphanumeric (max 100)                | ALPHANUMERIC",
            "free text only                        | FREE_TEXT",
    })
    void detectsKindFromCodificationText(String raw, String expectedKind) {
        CodificationDescriptor d = CodificationParser.parse(raw);
        assertThat(d.kind()).isEqualTo(CodificationKind.valueOf(expectedKind.trim()));
    }

    @Test
    void numericClosedListIsParsedFromBulletLines() {
        String raw = """
                One of the options:
                1 - ISO 6166 for ISIN code
                2 - CUSIP
                99 - Code attributed by the undertaking
                """;
        CodificationDescriptor d = CodificationParser.parse(raw);
        assertThat(d.kind()).isEqualTo(CodificationKind.CLOSED_LIST);
        assertThat(d.closedList()).extracting(CodificationDescriptor.ClosedListEntry::code)
                .containsExactly("1", "2", "99");
    }

    @Test
    void quotedTokenListIsParsedAsClosedList() {
        String raw = "\"Bullet\", \"Sinkable\", empty if non applicable";
        CodificationDescriptor d = CodificationParser.parse(raw);
        assertThat(d.kind()).isEqualTo(CodificationKind.CLOSED_LIST);
        assertThat(d.closedList()).extracting(CodificationDescriptor.ClosedListEntry::code)
                .containsExactly("Bullet", "Sinkable");
    }

    @Test
    void singleQuotedTokenIsNotAClosedList() {
        // Comment text with a single quoted phrase shouldn't trip the closed-list detector.
        String raw = "Alpha(1) (\"Y\" = yes only)";
        CodificationDescriptor d = CodificationParser.parse(raw);
        assertThat(d.kind()).isEqualTo(CodificationKind.ALPHA);
        assertThat(d.closedList()).isEmpty();
    }

    @Test
    void numericListWinsOverQuotedFallback() {
        // If both numeric "1 - foo" lines AND quoted tokens are present, numeric should win.
        String raw = """
                1 - first
                2 - second
                "ignored"
                "tokens"
                """;
        CodificationDescriptor d = CodificationParser.parse(raw);
        assertThat(d.closedList()).extracting(CodificationDescriptor.ClosedListEntry::code)
                .containsExactly("1", "2");
    }

    @Test
    void closedListEntryWithVeryLongLabelIsDropped() {
        // A "free-text bullet" with > 200 char label shouldn't be picked up.
        String longLabel = "x".repeat(220);
        String raw = "1 - " + longLabel + "\n2 - short label";
        CodificationDescriptor d = CodificationParser.parse(raw);
        // Only the short label survives; falls back to single -> dropped, then quoted fallback empty.
        assertThat(d.closedList()).extracting(CodificationDescriptor.ClosedListEntry::code)
                .containsExactly("2");
    }

    @Test
    void cicAlphanumericFourCodifiedAsCic() {
        CodificationDescriptor d = CodificationParser.parse("Alphanumeric (4)");
        assertThat(d.kind()).isEqualTo(CodificationKind.CIC);
        assertThat(d.maxLength()).contains(4);
    }

    @Test
    void alphaWithMaxLengthCaptured() {
        CodificationDescriptor d = CodificationParser.parse("Alpha (3)");
        assertThat(d.kind()).isEqualTo(CodificationKind.ALPHA);
        assertThat(d.maxLength()).contains(3);
    }

    @Test
    void alphanumericWithMaxLengthCaptured() {
        CodificationDescriptor d = CodificationParser.parse("Alphanum (max 255)");
        assertThat(d.kind()).isEqualTo(CodificationKind.ALPHANUMERIC);
        assertThat(d.maxLength()).contains(255);
    }

    @Test
    void closedListSubcodes() {
        // 8b, 105a etc. — used by NUM_DATA labels too.
        String raw = "8b - special\n105a - extra\n105b - extra2";
        CodificationDescriptor d = CodificationParser.parse(raw);
        assertThat(d.closedList()).extracting(CodificationDescriptor.ClosedListEntry::code)
                .containsExactly("8b", "105a", "105b");
    }

    @Test
    void numericRangeIsExpandedIntoClosedList() {
        // EMT V4.2 NUM 44 codification: "1-7 or Empty " (Risk_Tolerance_PRIIPS_Methodology).
        // Without range support the bullet regex misreads this as a single-entry closed list
        // ("1" with label "7 or Empty"), then any value ∈ {2..7} fails the FORMAT rule.
        CodificationDescriptor d = CodificationParser.parse("1-7 or Empty ");
        assertThat(d.kind()).isEqualTo(CodificationKind.CLOSED_LIST);
        assertThat(d.closedList()).extracting(CodificationDescriptor.ClosedListEntry::code)
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
    }

    @Test
    void numericRangeWithEnDashIsExpanded() {
        CodificationDescriptor d = CodificationParser.parse("1 – 6");
        assertThat(d.kind()).isEqualTo(CodificationKind.CLOSED_LIST);
        assertThat(d.closedList()).extracting(CodificationDescriptor.ClosedListEntry::code)
                .containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    void floatingDecimalExampleIsNotClosedList() {
        // EMT V4.2 cost fields (NUM 62, 66, 69-76, 84-90) carry a "Floating decimal." rubric
        // followed by inline examples like "1.15% = 0.0115". Without the post-separator
        // whitespace requirement, the bullet regex misreads "1" + "." + "15% = 0.0115" as a
        // single-entry closed list, then real cost values (e.g. 0.004) fail the FORMAT rule.
        String raw = "Floating decimal. \n1.15% = 0.0115\n5% =  0.05\nNumber must be >=0";
        CodificationDescriptor d = CodificationParser.parse(raw);
        assertThat(d.kind()).isEqualTo(CodificationKind.NUMERIC);
        assertThat(d.closedList()).isEmpty();
    }

    @Test
    void floatingDecimalWithLetterAlternativesKeepsLettersAsClosedList() {
        // EMT V4.2 NUM 55 (Minimum_Recommended_Holding_Period) accepts a numeric value or one
        // of V/S/M/L/H. The kind stays NUMERIC, but the letters live in closedList so FormatRule
        // can accept them as alternatives.
        CodificationDescriptor d = CodificationParser.parse("floating decimal  or V or S or M or L or H");
        assertThat(d.kind()).isEqualTo(CodificationKind.NUMERIC);
        assertThat(d.closedList()).extracting(CodificationDescriptor.ClosedListEntry::code)
                .containsExactly("V", "S", "M", "L", "H");
    }

    @Test
    void hasClosedListReflectsContent() {
        CodificationDescriptor empty = CodificationParser.parse("number with floating decimal");
        assertThat(empty.hasClosedList()).isFalse();

        CodificationDescriptor list = CodificationParser.parse("1 - foo\n2 - bar");
        assertThat(list.hasClosedList()).isTrue();
    }

    @Test
    void closedListIncludesPureLetterCodes() {
        String spec = """
                One of the options in the following closed list shall be used:
                1 - Government bonds
                2 - Corporate bonds
                3L - Listed equity
                3X - Unlisted equity
                4 - Collective Investment Undertakings
                5 - Structured notes
                6 - Collateralised securities
                7 - Cash and deposits
                8 - Mortgages and loans
                9 - Properties
                0 - Other investments (including receivables)
                A – Futures
                B – Call Options
                C – Put Options
                D – Swaps
                E – Forwards
                F – Credit derivatives
                L - Liabilities""";

        com.findatex.validator.spec.CodificationDescriptor d = com.findatex.validator.spec.CodificationParser.parse(spec);
        assertThat(d.kind()).isEqualTo(com.findatex.validator.spec.CodificationKind.CLOSED_LIST);
        assertThat(d.closedList())
                .extracting(com.findatex.validator.spec.CodificationDescriptor.ClosedListEntry::code)
                .containsExactlyInAnyOrder(
                        "1", "2", "3L", "3X", "4", "5", "6", "7", "8", "9", "0",
                        "A", "B", "C", "D", "E", "F", "L");
    }

    /** Field 35 spec text is free-text with an example, not a closed list. */
    @Test
    void exampleStringDoesNotBecomeClosedList() {
        CodificationDescriptor d = CodificationParser.parse(
                " e.g. \"BLOOMBERG\" or empty (if internal codification)");
        assertThat(d.kind()).isNotEqualTo(CodificationKind.CLOSED_LIST);
        assertThat(d.closedList()).isEmpty();
    }

    /** Pure-letter code with period must not match — narrative bullets like "i.e. ..." or "e.g. ...". */
    @Test
    void pureLetterCodeRequiresHyphenSeparator() {
        // Hyphen-separated single-letter list is fine (≥1 entries detected).
        CodificationDescriptor hyphen = CodificationParser.parse(
                "Pick one:\nA - Alpha\nB - Beta");
        assertThat(hyphen.closedList())
                .extracting(CodificationDescriptor.ClosedListEntry::code)
                .containsExactlyInAnyOrder("A", "B");

        // Period-separated single-letter is NOT treated as a closed list.
        CodificationDescriptor period = CodificationParser.parse(
                "i.e. some explanation goes here");
        assertThat(period.closedList()).isEmpty();
    }
}
