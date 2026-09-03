package com.findatex.validator.web.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The registry side of "Try an example" — no Quarkus needed, so this fails in
 * milliseconds when the classpath mount breaks.
 *
 * <p>Why it exists: the demo files reach the classpath through a chain of steps
 * that are all silent when they fail (the {@code <resources>} include in
 * {@code web-app/pom.xml}, the {@code .dockerignore} negation, Maven skipping a
 * missing resource directory). {@code SampleFiles.forTemplate} then reports
 * "no sample" and the UI simply hides the button — a working build with the
 * feature absent, which is exactly what shipped once (commit e3342db).
 */
class SampleFilesTest {

    @Test
    void everyTemplateHasADeclaredSample() {
        assertThat(SampleFiles.declared()).containsOnlyKeys("TPT", "EET", "EMT", "EPT");
    }

    @Test
    void everyDeclaredResourceIsOnTheClasspath() {
        for (Map.Entry<String, SampleFiles.Sample> e : SampleFiles.declared().entrySet()) {
            assertThat(SampleFiles.forTemplate(e.getKey()))
                    .as("%s sample resource %s is not on the classpath — check the "
                            + "<resources> include in web-app/pom.xml",
                            e.getKey(), e.getValue().resource())
                    .isPresent();
        }
    }

    @Test
    void samplesPointAtTheShowcaseFixtures() {
        // Not the numbered fixtures: those demonstrate one rule each. See the
        // SampleFiles javadoc.
        for (SampleFiles.Sample s : SampleFiles.declared().values()) {
            assertThat(s.resource()).endsWith("/00_showcase.xlsx");
            assertThat(s.filename()).endsWith(".xlsx");
        }
    }

    @Test
    void lookupIsCaseInsensitiveAndRejectsUnknownTemplates() {
        assertThat(SampleFiles.forTemplate("tpt")).isPresent();
        assertThat(SampleFiles.forTemplate("UFO")).isEmpty();
        assertThat(SampleFiles.forTemplate(null)).isEmpty();
    }

    @Test
    void recognisesTheDownloadNameOfEverySample() {
        for (SampleFiles.Sample s : SampleFiles.declared().values()) {
            assertThat(SampleFiles.isSampleFilename(s.filename())).isTrue();
            assertThat(SampleFiles.isSampleFilename("C:\\Downloads\\" + s.filename().toUpperCase())).isTrue();
        }
        assertThat(SampleFiles.isSampleFilename("20260331_TPTV7_LU123.xlsx")).isFalse();
        assertThat(SampleFiles.isSampleFilename(null)).isFalse();
    }
}
