package com.findatex.validator.web.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The demo files offered per template and spec version ("Try an example").
 *
 * <p>Why this exists: the tool needs a TPT/EET/EMT/EPT file to show anything at
 * all, and a first-time visitor evaluating it rarely has one to hand — they
 * would have to go find a real fund file before they can see whether this is
 * worth their time. One click removes that.
 *
 * <p>The files are the generator-driven fixtures from {@code samples/}, mounted
 * onto the classpath by the {@code <resources>} block in {@code pom.xml} rather
 * than copied here, so they cannot drift from what the
 * {@code *ExampleSamplesTest} suites assert. Adding a sample therefore means
 * widening <b>three</b> things — this list, that {@code <resources>} include and
 * the {@code .dockerignore} negation — or the feature silently disappears from
 * the container (see the {@code Application} startup warning).
 *
 * <p>{@code 00_showcase} is used rather than one of the numbered fixtures: it is
 * a full delivery (60 TPT positions across three funds, 25 share classes for
 * the other templates) carrying a curated spread of realistic defects. A clean
 * file scores 100 and shows an empty findings list, which demonstrates nothing;
 * a three-row fixture demonstrates one rule.
 *
 * <p>Each entry pins the spec <b>version</b> the fixture was generated for —
 * validating it against another version would produce findings that are
 * artefacts of the mismatch. A template may carry one sample per version (TPT
 * has V8.0 and V7.0); the UI offers the one matching the selected version and
 * falls back to the template's first entry. {@code SampleFilesTest} fails if a
 * pinned version disappears from the registry or a resource stops loading.
 */
public final class SampleFiles {

    /** Declaration order = UI order; per template, the first entry is the fallback. */
    private static final List<Sample> SAMPLES = List.of(
            new Sample("TPT", "V8.0",
                    "samples/tpt/v8/00_showcase.xlsx", "findatex-sample-tpt-v8.xlsx"),
            new Sample("TPT", "V7.0",
                    "samples/tpt/00_showcase.xlsx", "findatex-sample-tpt-v7.xlsx"),
            new Sample("EET", "V1.1.3",
                    "samples/eet/00_showcase.xlsx", "findatex-sample-eet-v1-1-3.xlsx"),
            new Sample("EMT", "V4.3",
                    "samples/emt/00_showcase.xlsx", "findatex-sample-emt-v4-3.xlsx"),
            new Sample("EPT", "V2.1",
                    "samples/ept/00_showcase.xlsx", "findatex-sample-ept-v2-1.xlsx"));

    private SampleFiles() {
    }

    /**
     * The demo files for a template whose resource is actually on the classpath,
     * in declaration order — empty when there is none, so a build without the
     * {@code samples/} directory simply hides the button instead of offering a
     * download that 404s.
     */
    public static List<Sample> forTemplate(String templateId) {
        if (templateId == null) return List.of();
        String id = templateId.toUpperCase(Locale.ROOT);
        return SAMPLES.stream()
                .filter(s -> s.templateId().equals(id))
                .filter(Sample::exists)
                .toList();
    }

    /** The demo file for one template version, or empty (unknown or resource absent). */
    public static Optional<Sample> forTemplate(String templateId, String version) {
        if (version == null) return Optional.empty();
        return forTemplate(templateId).stream()
                .filter(s -> s.version().equalsIgnoreCase(version.trim()))
                .findFirst();
    }

    /** All declared samples, whether or not the resource is present. */
    public static List<Sample> declared() {
        return SAMPLES;
    }

    /**
     * True when an upload carries the download name of one of the bundled
     * samples — the SPA re-uploads "Try an example" under exactly that name,
     * which lets the usage stats tell demo runs from real files.
     */
    public static boolean isSampleFilename(String filename) {
        if (filename == null) return false;
        String n = filename.trim();
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        for (Sample s : SAMPLES) {
            if (s.filename().equalsIgnoreCase(n)) return true;
        }
        return false;
    }

    public record Sample(String templateId, String version, String resource, String filename) {

        /** Streams the file; caller closes. Empty when the resource is absent. */
        public Optional<InputStream> open() {
            InputStream in = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resource);
            return Optional.ofNullable(in);
        }

        /** The download URL advertised in {@code GET /api/templates}. */
        public String url() {
            return "/api/samples/" + templateId + "/" + version;
        }

        public boolean exists() {
            Optional<InputStream> in = open();
            if (in.isEmpty()) return false;
            try {
                in.get().close();
            } catch (IOException ignored) {
                // Probing only — an unclosable stream still proves the resource is there.
            }
            return true;
        }
    }
}
