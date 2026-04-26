package com.tpt.validator.spec;

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
    void hasClosedListReflectsContent() {
        CodificationDescriptor empty = CodificationParser.parse("number with floating decimal");
        assertThat(empty.hasClosedList()).isFalse();

        CodificationDescriptor list = CodificationParser.parse("1 - foo\n2 - bar");
        assertThat(list.hasClosedList()).isTrue();
    }
}
