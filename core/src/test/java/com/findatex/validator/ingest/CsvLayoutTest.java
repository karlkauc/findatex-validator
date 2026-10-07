package com.findatex.validator.ingest;

import com.findatex.validator.domain.RawCell;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.report.SourceMirror;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV shapes seen (or to be expected) from real producers: the header is not always the
 * first line and not always spelled like the spec's column name.
 */
class CsvLayoutTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    private static final String HEADER =
            "1_Portfolio_identifying_data;3_Portfolio_name;4_Portfolio_currency_(B);12_CIC_code_of_the_instrument";
    private static final String BODY = "FR0000000001;Fund A;EUR;FR12\nFR0000000002;Fund B;USD;DE31\n";

    private static TptFile load(String csv) throws IOException {
        return new TptFileLoader(CATALOG).load(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "upload.csv");
    }

    private static void assertFullyMapped(TptFile file) {
        assertThat(file.headerToNumKey().values()).containsExactly("1", "3", "4", "12");
        assertThat(file.rows()).hasSize(2);
        assertThat(file.rows().get(0).rowIndex()).isEqualTo(1);
        assertThat(file.rows().get(0).stringValue("12")).contains("FR12");
        assertThat(file.rows().get(1).stringValue("3")).contains("Fund B");
        assertThat(HeaderMatch.of(file).mismatch()).isFalse();
    }

    @Test
    void plainFileInEveryDelimiter() throws IOException {
        for (String d : new String[] {";", ",", "|", "\t"}) {
            assertFullyMapped(load((HEADER + "\n" + BODY).replace(";", d)));
        }
    }

    @Test
    void titleLineBeforeHeader() throws IOException {
        assertFullyMapped(load("TPT delivery as of 2026-09-30\n" + HEADER + "\n" + BODY));
    }

    @Test
    void excelSeparatorHintBeforeHeader() throws IOException {
        assertFullyMapped(load("sep=;\n" + HEADER + "\n" + BODY));
    }

    @Test
    void separatorHintDecidesWhenNoHeaderIsRecognised() throws IOException {
        TptFile file = load("sep=,\nfoo,bar;baz\nx,y\n");
        assertThat(file.rawHeaders()).containsExactly("foo", "bar;baz");
        assertThat(file.rows()).hasSize(1);
    }

    @Test
    void leadingBlankLinesDoNotDecideTheDelimiter() throws IOException {
        for (String d : new String[] {",", "|"}) {
            assertFullyMapped(load("\r\n\r\n" + (HEADER + "\n" + BODY).replace(";", d)));
        }
    }

    @Test
    void quotedCellsInPipeFile() throws IOException {
        String quoted = ("\"" + HEADER.replace(";", "\"|\"") + "\"\n"
                + "\"FR0000000001\"|\"Fund A\"|\"EUR\"|\"FR12\"\nFR0000000002|Fund B|USD|DE31\n");
        assertFullyMapped(load(quoted));
    }

    @Test
    void headerSpellingVariants() throws IOException {
        assertFullyMapped(load("1. Portfolio identifying data;3 - Portfolio name;"
                + "0004_Portfolio_currency_(B);CIC code of the instrument\n" + BODY));
    }

    @Test
    void sourceCoordinatesCountTheLinesBeforeTheHeader() throws IOException {
        TptFile file = load("Some title\n\n" + HEADER + "\n" + BODY);
        RawCell cic = file.rows().get(1).get("12").orElseThrow();
        // Records: title (1), header (2), data (3, 4) — blank lines are not records.
        assertThat(cic.sourceRow()).isEqualTo(4);
        assertThat(cic.sourceCol()).isEqualTo(4);

        SourceMirror.SourceData mirror = SourceMirror.read(file);
        assertThat(mirror.headerRowIndex()).isEqualTo(1);
        assertThat(mirror.rows().get(cic.sourceRow() - 1).get(cic.sourceCol() - 1).asText()).isEqualTo("DE31");
    }

    @Test
    void mirrorOfPipeFileUsesTheSameColumns() throws IOException {
        TptFile file = load((HEADER + "\n" + BODY).replace(";", "|"));
        SourceMirror.SourceData mirror = SourceMirror.read(file);
        assertThat(mirror.rows().get(0)).hasSize(4);
        assertThat(mirror.rows().get(2).get(1).asText()).isEqualTo("Fund B");
    }

    @Test
    void fileWithoutHeaderRowIsAMismatch() throws IOException {
        // A data row read as header: stray numbers resolve to field numbers.
        TptFile file = load("FR0000000001;Fund A;EUR;1;2;x;y;z;q;w;e;r;t\nFR0000000002;Fund B;USD;1;2;x;y;z;q;w;e;r;t\n");
        HeaderMatch match = HeaderMatch.of(file);
        assertThat(match.recognised()).isEqualTo(2);
        assertThat(match.mismatch()).isTrue();
        assertThat(match.describe("TPT V7.0", "1_Portfolio_identifying_data"))
                .contains("only 2 of the 13")
                .contains("\"FR0000000001\", \"Fund A\", \"EUR\"")
                .contains("1_Portfolio_identifying_data");
    }

    @Test
    void numbersInADataRowDoNotMakeItTheHeader() throws IOException {
        TptFile file = load("Fund code;Fund name;Currency;Amount;Weight;Price\nX;Y;EUR;1;2;3\n");
        assertThat(file.rawHeaders()).startsWith("Fund code", "Fund name");
        assertThat(file.rows()).hasSize(1);
        assertThat(HeaderMatch.of(file).mismatch()).isTrue();
    }

    @Test
    void numbersOnlyHeaderStillWorks() throws IOException {
        assertFullyMapped(load("\n1,3,4,12\n" + BODY.replace(";", ",")));
    }

    @Test
    void foreignHeadersAreAMismatch() throws IOException {
        HeaderMatch match = HeaderMatch.of(load("99001_Bogus|99002_Other\na|b\n"));
        assertThat(match.mismatch()).isTrue();
        assertThat(match.describe("TPT V7.0", "x")).contains("none of the 2");
    }
}
