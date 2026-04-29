package com.findatex.validator.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FolderScannerTest {

    private final FolderScanner scanner = new FolderScanner();

    @Test
    void acceptsSupportedExtensions(@TempDir Path d) throws IOException {
        Files.createFile(d.resolve("a.xlsx"));
        Files.createFile(d.resolve("b.xlsm"));
        Files.createFile(d.resolve("c.csv"));
        Files.createFile(d.resolve("d.tsv"));

        FolderScanner.ScanResult result = scanner.scan(d);

        assertThat(result.accepted()).extracting(p -> p.getFileName().toString())
                .containsExactly("a.xlsx", "b.xlsm", "c.csv", "d.tsv");
        assertThat(result.rejected()).isEmpty();
    }

    @Test
    void rejectsHiddenDotFiles(@TempDir Path d) throws IOException {
        Files.createFile(d.resolve(".hidden.xlsx"));
        Files.createFile(d.resolve(".DS_Store"));
        Files.createFile(d.resolve("real.xlsx"));

        FolderScanner.ScanResult result = scanner.scan(d);

        assertThat(result.accepted()).extracting(p -> p.getFileName().toString())
                .containsExactly("real.xlsx");
        assertThat(result.rejected()).hasSize(2);
    }

    @Test
    void rejectsOfficeLockFiles(@TempDir Path d) throws IOException {
        Files.createFile(d.resolve("~$open.xlsx"));
        Files.createFile(d.resolve("real.xlsx"));

        FolderScanner.ScanResult result = scanner.scan(d);

        assertThat(result.accepted()).extracting(p -> p.getFileName().toString())
                .containsExactly("real.xlsx");
    }

    @Test
    void rejectsReportXlsxSuffixCaseInsensitive(@TempDir Path d) throws IOException {
        Files.createFile(d.resolve("fund_a.report.xlsx"));
        Files.createFile(d.resolve("fund_b.REPORT.XLSX"));
        Files.createFile(d.resolve("delivery.combined.report.xlsx"));
        Files.createFile(d.resolve("fund_c.xlsx"));

        FolderScanner.ScanResult result = scanner.scan(d);

        assertThat(result.accepted()).extracting(p -> p.getFileName().toString())
                .containsExactly("fund_c.xlsx");
        assertThat(result.rejected()).hasSize(3);
    }

    @Test
    void rejectsUnsupportedExtensions(@TempDir Path d) throws IOException {
        Files.createFile(d.resolve("readme.txt"));
        Files.createFile(d.resolve("notes.md"));
        Files.createFile(d.resolve("archive.zip"));
        Files.createFile(d.resolve("real.xlsx"));

        FolderScanner.ScanResult result = scanner.scan(d);

        assertThat(result.accepted()).extracting(p -> p.getFileName().toString())
                .containsExactly("real.xlsx");
        assertThat(result.rejected()).hasSize(3);
    }

    @Test
    void ignoresSubdirectories(@TempDir Path d) throws IOException {
        Files.createDirectory(d.resolve("sub"));
        Files.createFile(d.resolve("sub").resolve("nested.xlsx"));
        Files.createFile(d.resolve("top.xlsx"));

        FolderScanner.ScanResult result = scanner.scan(d);

        assertThat(result.accepted()).extracting(p -> p.getFileName().toString())
                .containsExactly("top.xlsx");
    }

    @Test
    void returnsAlphabeticalOrderCaseInsensitive(@TempDir Path d) throws IOException {
        Files.createFile(d.resolve("Charlie.xlsx"));
        Files.createFile(d.resolve("alpha.xlsx"));
        Files.createFile(d.resolve("Bravo.xlsx"));

        FolderScanner.ScanResult result = scanner.scan(d);

        assertThat(result.accepted()).extracting(p -> p.getFileName().toString())
                .containsExactly("alpha.xlsx", "Bravo.xlsx", "Charlie.xlsx");
    }

    @Test
    void emptyFolderReturnsEmpty(@TempDir Path d) throws IOException {
        FolderScanner.ScanResult result = scanner.scan(d);
        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejected()).isEmpty();
    }

    @Test
    void nonFolderInputThrows(@TempDir Path d) throws IOException {
        Path file = Files.createFile(d.resolve("a.xlsx"));
        assertThatThrownBy(() -> scanner.scan(file)).isInstanceOf(IOException.class);
    }
}
