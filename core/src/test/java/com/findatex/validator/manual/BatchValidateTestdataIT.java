package com.findatex.validator.manual;

import com.findatex.validator.batch.BatchFileStatus;
import com.findatex.validator.batch.BatchResult;
import com.findatex.validator.batch.BatchSummary;
import com.findatex.validator.batch.BatchValidationOptions;
import com.findatex.validator.batch.BatchValidationService;
import com.findatex.validator.config.AppSettings;
import com.findatex.validator.report.CombinedXlsxReportWriter;
import com.findatex.validator.report.GenerationUi;
import com.findatex.validator.report.ScoreCategory;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateId;
import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.template.eet.EetTemplate;
import com.findatex.validator.template.emt.EmtTemplate;
import com.findatex.validator.template.ept.EptTemplate;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Opt-in manual driver — feeds every recognised file under
 * {@code /home/karl/webdav/findatex-testdata/} (or {@code -Dbatch.testdata.dir=...})
 * through the validator and writes per-(template, version) combined XLSX reports
 * plus a top-level {@code RESULTS.md} into that same folder.
 *
 * <p>Invoke with:
 * <pre>{@code
 *   mvn -pl core -am test -Dtest=BatchValidateTestdataIT -Dbatch.testdata=true
 * }</pre>
 *
 * <p>The test is skipped unless {@code -Dbatch.testdata=true} is passed, so the
 * regular {@code mvn test} baseline is untouched.
 */
@EnabledIfSystemProperty(named = "batch.testdata", matches = "true")
class BatchValidateTestdataIT {

    private static final Path TESTDATA = Path.of(System.getProperty(
            "batch.testdata.dir", "/home/karl/webdav/findatex-testdata"));

    private static final Pattern V_4_3 = Pattern.compile("v[_ -]?4[._-]?3", Pattern.CASE_INSENSITIVE);
    private static final Pattern V_4_2 = Pattern.compile("v[_ -]?4[._-]?2", Pattern.CASE_INSENSITIVE);
    private static final Pattern V_4_X = Pattern.compile("v[_ -]?4(?![._-]?\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern V_1_1_2 = Pattern.compile("v[_ -]?1[._-]?1[._-]?2", Pattern.CASE_INSENSITIVE);
    private static final Pattern V_2_1 = Pattern.compile("v[_ -]?2[._-]?1", Pattern.CASE_INSENSITIVE);
    private static final Pattern V_2_0 = Pattern.compile("v[_ -]?2[._-]?0", Pattern.CASE_INSENSITIVE);

    private record Skip(Path path, String reason) {}
    private record Key(TemplateId tmpl, TemplateVersion ver) {}
    private record Outcome(Key key, BatchSummary summary, Path xlsxPath) {}

    @Test
    void runBatch() throws Exception {
        if (!Files.isDirectory(TESTDATA)) {
            throw new IllegalStateException("Testdata directory not found: " + TESTDATA);
        }
        TemplateRegistry.init();

        List<Path> candidates = discover();
        System.out.println("[batch] discovered " + candidates.size() + " candidate file(s)");

        List<Skip> skipped = new ArrayList<>();
        Map<Key, List<Path>> grouped = new LinkedHashMap<>();
        for (Path p : candidates) {
            String reason = preFlight(p);
            if (reason != null) {
                skipped.add(new Skip(p, reason));
                continue;
            }
            Key key = classify(p);
            if (key == null) {
                skipped.add(new Skip(p, "unrecognised — no template hint in filename"));
                continue;
            }
            grouped.computeIfAbsent(key, __ -> new ArrayList<>()).add(p);
        }

        List<Outcome> outcomes = new ArrayList<>();
        for (Map.Entry<Key, List<Path>> entry : grouped.entrySet()) {
            Key key = entry.getKey();
            List<Path> files = entry.getValue();
            System.out.println("[batch] " + key.tmpl() + " " + key.ver().version()
                    + " — " + files.size() + " file(s)");

            TemplateDefinition tdef = TemplateRegistry.of(key.tmpl());
            TemplateVersion v = key.ver();
            SpecCatalog cat = tdef.specLoaderFor(v).load();
            ProfileSet profiles = tdef.profilesFor(v);
            Set<ProfileKey> activeProfiles = Set.copyOf(profiles.all());

            BatchValidationOptions opts = new BatchValidationOptions(
                    tdef, v, activeProfiles,
                    /*externalValidationEnabled*/ false,
                    AppSettings.defaults(),
                    /*externalCacheDir*/ null);
            BatchSummary summary = new BatchValidationService(cat, opts)
                    .run(files, () -> false);

            Path xlsx = TESTDATA.resolve(key.tmpl() + "-" + v.version() + "-report.xlsx");
            new CombinedXlsxReportWriter(cat, profiles, v, GenerationUi.CLI)
                    .write(summary, xlsx);
            outcomes.add(new Outcome(key, summary, xlsx));
            System.out.println("[batch]   wrote " + xlsx);
        }

        Path md = TESTDATA.resolve("RESULTS.md");
        writeMarkdownSummary(md, outcomes, skipped);
        System.out.println("[batch] wrote " + md);
    }

    private static List<Path> discover() throws IOException {
        try (var stream = Files.walk(TESTDATA)) {
            return stream.filter(Files::isRegularFile)
                    .filter(BatchValidateTestdataIT::isCandidate)
                    .sorted()
                    .toList();
        }
    }

    private static boolean isCandidate(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.startsWith("~$") || n.startsWith(".")) return false;
        if (n.endsWith("-report.xlsx")) return false;
        return n.endsWith(".xlsx") || n.endsWith(".csv");
    }

    /** @return skip reason, or null if the file passes the pre-flight. */
    private static String preFlight(Path p) {
        long size;
        try {
            size = Files.size(p);
        } catch (IOException e) {
            return "I/O error reading size: " + e.getMessage();
        }
        if (size == 0L) return "empty file (0 bytes)";
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".xlsx")) {
            if (size < 4096L) return "stub / error response (" + size + " bytes)";
            try (ZipFile zf = new ZipFile(p.toFile())) {
                if (zf.getEntry("[Content_Types].xml") == null) {
                    return "not a valid xlsx (missing [Content_Types].xml)";
                }
            } catch (IOException e) {
                return "not a valid xlsx (zip corrupt)";
            }
        } else if (n.endsWith(".csv")) {
            if (size < 32L) return "csv too small to contain a header (" + size + " bytes)";
        }
        return null;
    }

    /** @return (template, version), or null when filename has no template hint. */
    private static Key classify(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean emt = n.contains("emt");
        boolean eet = n.contains("eet");
        boolean ept = n.contains("ept");
        if ((emt ? 1 : 0) + (eet ? 1 : 0) + (ept ? 1 : 0) != 1) {
            // ambiguous or missing — bail
            return null;
        }
        if (emt) {
            if (V_4_3.matcher(n).find()) return new Key(TemplateId.EMT, EmtTemplate.V4_3);
            if (V_4_2.matcher(n).find()) return new Key(TemplateId.EMT, EmtTemplate.V4_2);
            if (V_4_X.matcher(n).find()) return new Key(TemplateId.EMT, EmtTemplate.V4_2);
            return new Key(TemplateId.EMT, EmtTemplate.V4_3);
        }
        if (eet) {
            if (V_1_1_2.matcher(n).find()) return new Key(TemplateId.EET, EetTemplate.V1_1_2);
            return new Key(TemplateId.EET, EetTemplate.V1_1_3);
        }
        // ept
        if (V_2_0.matcher(n).find()) return new Key(TemplateId.EPT, EptTemplate.V2_0);
        if (V_2_1.matcher(n).find()) return new Key(TemplateId.EPT, EptTemplate.V2_1);
        return new Key(TemplateId.EPT, EptTemplate.V2_1);
    }

    private static void writeMarkdownSummary(Path out,
                                             List<Outcome> outcomes,
                                             List<Skip> skipped) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("# FinDatEx Validator — Testdata Run\n\n");
        b.append("Generated: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("\n\n");
        b.append("Source folder: `").append(TESTDATA).append("`\n\n");

        b.append("## Summary\n\n");
        b.append("| Template | Version | Files | Mean OVERALL | Errors | Warnings | Infos | Detail |\n");
        b.append("|----------|---------|------:|-------------:|-------:|---------:|------:|--------|\n");
        for (Outcome o : outcomes) {
            BatchSummary s = o.summary();
            b.append("| ").append(o.key().tmpl())
                    .append(" | ").append(o.key().ver().version())
                    .append(" | ").append(s.results().size())
                    .append(" | ").append(formatScore(s.aggregateOverallScore()))
                    .append(" | ").append(s.aggregateErrors())
                    .append(" | ").append(s.aggregateWarnings())
                    .append(" | ").append(s.aggregateInfos())
                    .append(" | [").append(out.getParent().relativize(o.xlsxPath()))
                    .append("](./").append(out.getParent().relativize(o.xlsxPath())).append(") |\n");
        }
        b.append('\n');

        for (Outcome o : outcomes) {
            b.append("## ").append(o.key().tmpl()).append(' ').append(o.key().ver().version()).append("\n\n");
            b.append("Detail report: [").append(out.getParent().relativize(o.xlsxPath())).append("]")
                    .append("(./").append(out.getParent().relativize(o.xlsxPath())).append(")\n\n");
            b.append("| File | Status | Rows | OVERALL | Errors | Warnings | Infos | Time |\n");
            b.append("|------|--------|-----:|--------:|-------:|---------:|------:|-----:|\n");
            for (BatchResult r : o.summary().results()) {
                Path rel = TESTDATA.relativize(r.source());
                b.append("| `").append(rel).append('`')
                        .append(" | ").append(r.status())
                        .append(" | ").append(r.status() == BatchFileStatus.OK && r.file() != null
                                ? Integer.toString(r.file().rows().size()) : "—")
                        .append(" | ").append(formatScoreCell(r))
                        .append(" | ").append(countSeverity(r, Severity.ERROR))
                        .append(" | ").append(countSeverity(r, Severity.WARNING))
                        .append(" | ").append(countSeverity(r, Severity.INFO))
                        .append(" | ").append(formatElapsed(r.elapsed().toMillis()))
                        .append(" |\n");
            }
            b.append('\n');
            // surface non-OK error messages
            List<BatchResult> failed = o.summary().results().stream()
                    .filter(r -> r.status() != BatchFileStatus.OK)
                    .toList();
            if (!failed.isEmpty()) {
                b.append("### Errors\n\n");
                for (BatchResult r : failed) {
                    Path rel = TESTDATA.relativize(r.source());
                    b.append("- `").append(rel).append("` — ")
                            .append(r.status()).append(": ")
                            .append(r.errorMessage() == null ? "(no message)" : r.errorMessage())
                            .append('\n');
                }
                b.append('\n');
            }
        }

        b.append("## Skipped / not validated\n\n");
        if (skipped.isEmpty()) {
            b.append("_(none — every candidate file was classified and validated)_\n");
        } else {
            b.append("| File | Reason |\n");
            b.append("|------|--------|\n");
            for (Skip s : skipped) {
                Path rel = TESTDATA.relativize(s.path());
                b.append("| `").append(rel).append("` | ").append(s.reason()).append(" |\n");
            }
        }
        b.append('\n');

        Files.writeString(out, b.toString());
    }

    private static String formatScore(OptionalDouble v) {
        return v.isPresent() ? String.format(Locale.ROOT, "%.1f%%", v.getAsDouble() * 100.0) : "—";
    }

    private static String formatScoreCell(BatchResult r) {
        if (r.status() != BatchFileStatus.OK || r.report() == null) return "—";
        Double v = r.report().scores().get(ScoreCategory.OVERALL);
        return v == null ? "—" : String.format(Locale.ROOT, "%.1f%%", v * 100.0);
    }

    private static long countSeverity(BatchResult r, Severity sev) {
        if (r.findings() == null) return 0L;
        long n = 0L;
        for (Finding f : r.findings()) if (f.severity() == sev) n++;
        return n;
    }

    private static String formatElapsed(long ms) {
        return ms < 1000 ? ms + " ms" : String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
    }
}
