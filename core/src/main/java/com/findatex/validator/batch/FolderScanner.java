package com.findatex.validator.batch;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Scans a single folder (top-level only; no recursion) for files supported by
 * {@code TptFileLoader} — {@code .xlsx}, {@code .xlsm}, {@code .csv}, {@code .tsv}.
 *
 * <p>Excludes hidden files (POSIX-style dot prefix), Office lock files ({@code ~$*})
 * and previously-written reports ({@code *.report.xlsx}). Subdirectories are skipped
 * silently.
 *
 * <p>Filenames are returned in case-insensitive alphabetical order so the resulting
 * batch is reproducible.
 */
public final class FolderScanner {

    private static final List<String> ACCEPTED_EXTENSIONS =
            List.of(".xlsx", ".xlsm", ".csv", ".tsv");

    /** Result of a scan: the files we will attempt to validate, plus those skipped. */
    public record ScanResult(List<Path> accepted, List<Path> rejected) {
        public ScanResult {
            accepted = List.copyOf(accepted);
            rejected = List.copyOf(rejected);
        }
    }

    public ScanResult scan(Path folder) throws IOException {
        if (folder == null || !Files.isDirectory(folder)) {
            throw new IOException("Not a folder: " + folder);
        }
        List<Path> accepted = new ArrayList<>();
        List<Path> rejected = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) continue;
                String name = p.getFileName().toString();
                if (isAccepted(name)) accepted.add(p);
                else rejected.add(p);
            }
        }
        accepted.sort(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        rejected.sort(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        return new ScanResult(accepted, rejected);
    }

    private static boolean isAccepted(String name) {
        if (name.isEmpty()) return false;
        if (name.charAt(0) == '.') return false;                    // hidden (POSIX)
        if (name.startsWith("~$")) return false;                    // Office lock
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".report.xlsx")) return false;           // prior single-file export
        if (lower.endsWith(".combined.report.xlsx")) return false;  // prior combined export
        for (String ext : ACCEPTED_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
}
