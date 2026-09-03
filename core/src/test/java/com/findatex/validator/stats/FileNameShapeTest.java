package com.findatex.validator.stats;

import com.findatex.validator.batch.BatchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileNameShapeTest {

    @ParameterizedTest
    @CsvSource({
            "20260331_TPTV7_LU1234567890.xlsx, xlsx, dated_template",
            "20240630-EET_V1.1_fund.csv, csv, dated_template",
            "20250101_emt_v4.xlsm, xlsx, dated_template",
            "UBS_Asset_Management_EPT_UBSFML_V2.1_de.csv, csv, template_token",
            "my_tpt_file.xlsx, xlsx, template_token",
            "findatex-sample-tpt-v7.xlsx, xlsx, template_token",
            "Generated_EMT_Amundi.csv, csv, template_token",
            "00_showcase.xlsx, xlsx, other",
            "positions.tsv, csv, other",
            "adept_report.xlsx, xlsx, other",
            "C:\\Users\\x\\20260331_TPTV7_a.xlsx, xlsx, dated_template",
    })
    void formatAndPattern(String name, String format, String pattern) {
        assertThat(FileNameShape.format(name)).isEqualTo(format);
        assertThat(FileNameShape.pattern(name)).isEqualTo(pattern);
    }

    @Test
    void unknownExtensionAndBlankName() {
        assertThat(FileNameShape.format("report.pdf")).isNull();
        assertThat(FileNameShape.format(null)).isNull();
        assertThat(FileNameShape.pattern(null)).isEqualTo("other");
        assertThat(FileNameShape.pattern("  ")).isEqualTo("other");
        assertThat(FileNameShape.of("x.pdf", -5L).bytes()).isNull();
        assertThat(FileNameShape.of((Path) null)).isEqualTo(UsageEvent.Input.UNKNOWN);
    }

    @Test
    void inputNeverCarriesTheName() {
        UsageEvent.Input in = FileNameShape.of("20260331_TPTV7_TOPSECRET.xlsx", 10L);
        assertThat(in.toString()).doesNotContain("TOPSECRET").doesNotContain("20260331");
    }

    @Test
    void ofPathReadsSize(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("20260331_TPTV7_x.csv");
        Files.writeString(f, "a;b;c\n");
        UsageEvent.Input in = FileNameShape.of(f);
        assertThat(in.bytes()).isEqualTo(6L);
        assertThat(in.format()).isEqualTo("csv");
        assertThat(in.namePattern()).isEqualTo("dated_template");
        assertThat(FileNameShape.of(dir.resolve("missing.xlsx")).bytes()).isNull();
    }

    @Test
    void batchAggregatesSizesFormatsAndPattern(@TempDir Path dir) throws Exception {
        Path a = dir.resolve("20260331_TPTV7_a.xlsx");
        Path b = dir.resolve("other_tpt.csv");
        Path c = dir.resolve("plain.xlsx");
        Files.write(a, new byte[100]);
        Files.write(b, new byte[50]);
        Files.write(c, new byte[1]);
        UsageEvent.Input mixed = FileNameShape.ofBatch(List.of(res(a), res(b), res(c)));
        assertThat(mixed.format()).isEqualTo("mixed");
        assertThat(mixed.bytes()).isEqualTo(151L);
        assertThat(mixed.namePattern()).isEqualTo("template_token");

        assertThat(FileNameShape.ofBatch(List.of(res(a))).namePattern()).isEqualTo("dated_template");
        assertThat(FileNameShape.ofBatch(List.of(res(c))).namePattern()).isEqualTo("other");
        assertThat(FileNameShape.ofBatch(List.of(res(a), res(c))).format()).isEqualTo("xlsx");
        assertThat(FileNameShape.ofBatch(List.of())).isEqualTo(UsageEvent.Input.UNKNOWN);
    }

    private static BatchResult res(Path p) {
        return BatchResult.loadError(p, "x", Duration.ZERO);
    }
}
