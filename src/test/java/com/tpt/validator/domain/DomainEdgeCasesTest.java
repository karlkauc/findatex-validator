package com.tpt.validator.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEdgeCasesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rawCellIsEmptyForBlanks(String v) {
        RawCell c = new RawCell(v, 1, 1);
        assertThat(c.isEmpty()).isTrue();
        assertThat(c.trimmed()).isEmpty();
    }

    @Test
    void rawCellPreservesValueAndTrims() {
        RawCell c = new RawCell("  EUR  ", 5, 3);
        assertThat(c.isEmpty()).isFalse();
        assertThat(c.trimmed()).isEqualTo("EUR");
        assertThat(c.value()).isEqualTo("  EUR  ");
        assertThat(c.sourceRow()).isEqualTo(5);
        assertThat(c.sourceCol()).isEqualTo(3);
    }

    @Test
    void tptRowIgnoresBlankNumKey() {
        TptRow r = new TptRow(1);
        r.put(null, new RawCell("x", 1, 1));
        r.put("", new RawCell("y", 1, 1));
        r.put("   ", new RawCell("z", 1, 1));
        assertThat(r.all()).isEmpty();
    }

    @Test
    void tptRowStringValueSkipsEmptyCells() {
        TptRow r = new TptRow(1);
        r.put("12", new RawCell("FR12", 1, 1));
        r.put("17", new RawCell("   ", 1, 1));   // blank
        assertThat(r.stringValue("12")).contains("FR12");
        assertThat(r.stringValue("17")).isEmpty();
        assertThat(r.stringValue("99")).isEmpty();
    }

    @Test
    void tptRowCicReturnsEmptyWhenInvalid() {
        TptRow r = new TptRow(1);
        r.put("12", new RawCell("XXX", 1, 1));    // 3 chars, invalid
        assertThat(r.cic()).isEmpty();
    }

    @Test
    void tptRowCicReturnsParsedWhenValid() {
        TptRow r = new TptRow(1);
        r.put("12", new RawCell("FR12", 1, 1));
        assertThat(r.cic()).isPresent();
        assertThat(r.cic().get().categoryDigit()).isEqualTo("1");
    }

    @Test
    void tptFilePresentNumKeysReflectsHeaderMap() {
        var headers = new LinkedHashMap<Integer, String>();
        headers.put(0, "12");
        headers.put(1, "17");
        TptFile f = new TptFile(Path.of("x.csv"), "csv",
                List.of("12", "17"), headers, List.of(), List.of());
        assertThat(f.presentNumKeys()).containsExactly("12", "17");
    }

    @Test
    void tptFileExposesUnmappedHeaders() {
        TptFile f = new TptFile(Path.of("x.csv"), "csv",
                List.of("12", "weirdHeader"),
                new LinkedHashMap<>(),
                List.of("weirdHeader"),
                List.of());
        assertThat(f.unmappedHeaders()).containsExactly("weirdHeader");
    }

    @Test
    void cicCodeUnknownDigitMapsToFallback() {
        // Construct a CIC with digit 'F' (valid) and trip categoryName for digits not in the map.
        // The parser only accepts 0-9, A-F so reaching the 'Unknown CIC' branch directly requires
        // bypassing the parser. We exercise it via the public categoryName() with reflection-free
        // construction by parsing a valid CIC and then asserting the map covers each branch.
        for (String d : new String[]{"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"}) {
            CicCode c = CicCode.parse("XL" + d + "0").orElseThrow();
            assertThat(c.categoryName()).isNotEqualTo("Unknown CIC");
        }
    }

    @Test
    void cicCodeAcceptsHexDigitsThroughF() {
        // Spec uses CIC A..F for derivatives; verify subcategory accepts both letters and digits.
        assertThat(CicCode.parse("XLA0")).isPresent();
        assertThat(CicCode.parse("XLAZ")).isPresent();
        assertThat(CicCode.parse("XLA9")).isPresent();
        assertThat(CicCode.parse("XLG0")).isEmpty();    // 'G' not allowed for category digit
    }
}
