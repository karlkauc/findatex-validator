package com.findatex.validator.web.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The demo file offered per template ("Try a sample file").
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
 * widening <b>three</b> things — this map, that {@code <resources>} include and
 * the {@code .dockerignore} negation — or the feature silently disappears from
 * the container (see the {@code Application} startup warning).
 *
 * <p>{@code 00_showcase} is used rather than one of the numbered fixtures: it is
 * a full delivery (60 TPT positions across three funds, 25 share classes for
 * the other templates) carrying a curated spread of realistic defects. A clean
 * file scores 100 and shows an empty findings list, which demonstrates nothing;
 * a three-row fixture demonstrates one rule.
 *
 * <p>Each entry pins the spec <b>version</b> the fixture was generated for
 * (TPT's is V7.0, not the latest V8.0) — validating it against another version
 * would produce findings that are artefacts of the mismatch.
 * {@code SampleFilesTest} fails if a pinned version disappears from the
 * registry or a resource stops loading.
 */
public final class SampleFiles {

    /** Keyed by {@code TemplateId.name()}; insertion order = UI order. */
    private static final Map<String, Sample> SAMPLES = new LinkedHashMap<>();

    static {
        SAMPLES.put("TPT", new Sample("TPT", "V7.0",
                "samples/tpt/00_showcase.xlsx", "findatex-sample-tpt-v7.xlsx"));
        SAMPLES.put("EET", new Sample("EET", "V1.1.3",
                "samples/eet/00_showcase.xlsx", "findatex-sample-eet-v1-1-3.xlsx"));
        SAMPLES.put("EMT", new Sample("EMT", "V4.3",
                "samples/emt/00_showcase.xlsx", "findatex-sample-emt-v4-3.xlsx"));
        SAMPLES.put("EPT", new Sample("EPT", "V2.1",
                "samples/ept/00_showcase.xlsx", "findatex-sample-ept-v2-1.xlsx"));
    }

    private SampleFiles() {
    }

    /**
     * The demo file for a template, or empty when there is none — including the
     * case where the classpath resource is missing, so a build without the
     * {@code samples/} directory simply hides the button instead of offering a
     * download that 404s.
     */
    public static Optional<Sample> forTemplate(String templateId) {
        if (templateId == null) return Optional.empty();
        Sample sample = SAMPLES.get(templateId.toUpperCase(java.util.Locale.ROOT));
        if (sample == null || !sample.exists()) return Optional.empty();
        return Optional.of(sample);
    }

    /** All declared samples, whether or not the resource is present. */
    public static Map<String, Sample> declared() {
        return Map.copyOf(SAMPLES);
    }

    public record Sample(String templateId, String version, String resource, String filename) {

        /** Streams the file; caller closes. Empty when the resource is absent. */
        public Optional<InputStream> open() {
            InputStream in = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resource);
            return Optional.ofNullable(in);
        }

        boolean exists() {
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
