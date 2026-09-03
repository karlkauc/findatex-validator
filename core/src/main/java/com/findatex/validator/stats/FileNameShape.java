package com.findatex.validator.stats;

import com.findatex.validator.batch.BatchResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reduces a file (name + size) to the non-identifying classes carried by
 * {@link UsageEvent.Input}. This is the <em>only</em> place that may look at a
 * file name for statistics purposes; it must never return any part of the name.
 *
 * <p>Naming pattern is a documented heuristic — FinDatEx does not mandate a
 * file name, but many producers use {@code YYYYMMDD_TPTV7_…}:
 * <ul>
 *   <li>{@code dated_template} — starts with an 8-digit date followed by a
 *       template token ({@code 20260331_TPTV7_LU123.xlsx})</li>
 *   <li>{@code template_token} — a template token appears anywhere
 *       ({@code UBS_EPT_V2.1_en.csv}, {@code my_tpt_file.xlsx})</li>
 *   <li>{@code other} — anything else ({@code 00_showcase.xlsx})</li>
 * </ul>
 */
public final class FileNameShape {

    public static final String FORMAT_XLSX = "xlsx";
    public static final String FORMAT_CSV = "csv";
    public static final String FORMAT_MIXED = "mixed";

    public static final String PATTERN_DATED_TEMPLATE = "dated_template";
    public static final String PATTERN_TEMPLATE_TOKEN = "template_token";
    public static final String PATTERN_OTHER = "other";

    private static final Pattern DATED = Pattern.compile(
            "^\\d{8}[_\\-](TPT|EET|EMT|EPT)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN = Pattern.compile(
            "(^|[^A-Za-z])(TPT|EET|EMT|EPT)([^A-Za-z]|$)", Pattern.CASE_INSENSITIVE);

    private FileNameShape() {
    }

    /** {@code xlsx} / {@code csv} by extension (same rules as the loader), else null. */
    public static String format(String filename) {
        if (filename == null) return null;
        String n = filename.toLowerCase(Locale.ROOT);
        if (n.endsWith(".xlsx") || n.endsWith(".xlsm")) return FORMAT_XLSX;
        if (n.endsWith(".csv") || n.endsWith(".tsv") || n.endsWith(".txt")) return FORMAT_CSV;
        return null;
    }

    /** Naming-pattern class; never any part of the name. */
    public static String pattern(String filename) {
        if (filename == null || filename.isBlank()) return PATTERN_OTHER;
        String base = baseName(filename);
        if (DATED.matcher(base).find()) return PATTERN_DATED_TEMPLATE;
        if (TOKEN.matcher(base).find()) return PATTERN_TEMPLATE_TOKEN;
        return PATTERN_OTHER;
    }

    /** Web upload: name from the multipart part plus the declared size. */
    public static UsageEvent.Input of(String filename, Long bytes) {
        return new UsageEvent.Input(format(filename), bytes == null || bytes < 0 ? null : bytes,
                pattern(filename));
    }

    /** Desktop single file: size read from disk, unknown on failure. */
    public static UsageEvent.Input of(Path path) {
        if (path == null) return UsageEvent.Input.UNKNOWN;
        String name = path.getFileName() == null ? null : path.getFileName().toString();
        Long size;
        try {
            size = Files.size(path);
        } catch (IOException | RuntimeException e) {
            size = null;
        }
        return of(name, size);
    }

    /**
     * Desktop batch: sizes summed, {@code mixed} when formats differ, pattern
     * {@code dated_template} only if every file follows it, {@code other} only
     * if none carries a template token, else {@code template_token}.
     */
    public static UsageEvent.Input ofBatch(List<BatchResult> results) {
        if (results == null || results.isEmpty()) return UsageEvent.Input.UNKNOWN;
        String format = null;
        boolean mixed = false;
        long bytes = 0;
        boolean anyBytes = false;
        int dated = 0;
        int token = 0;
        for (BatchResult r : results) {
            UsageEvent.Input one = of(r.source());
            if (one.format() != null) {
                if (format == null) format = one.format();
                else if (!format.equals(one.format())) mixed = true;
            }
            if (one.bytes() != null) {
                bytes += one.bytes();
                anyBytes = true;
            }
            if (PATTERN_DATED_TEMPLATE.equals(one.namePattern())) dated++;
            else if (PATTERN_TEMPLATE_TOKEN.equals(one.namePattern())) token++;
        }
        String pattern;
        if (dated == results.size()) pattern = PATTERN_DATED_TEMPLATE;
        else if (dated + token == 0) pattern = PATTERN_OTHER;
        else pattern = PATTERN_TEMPLATE_TOKEN;
        return new UsageEvent.Input(mixed ? FORMAT_MIXED : format, anyBytes ? bytes : null, pattern);
    }

    private static String baseName(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return slash >= 0 ? filename.substring(slash + 1) : filename;
    }
}
