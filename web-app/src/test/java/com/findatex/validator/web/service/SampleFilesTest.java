package com.findatex.validator.web.service;

import org.junit.jupiter.api.Test;


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
        assertThat(SampleFiles.declared()).extracting(SampleFiles.Sample::templateId)
                .containsOnly("TPT", "EET", "EMT", "EPT");
    }

    @Test
    void tptOffersADemoForBothBundledVersions() {
        // "Try an example" follows the version picker — TPT defaults to V8.0, and
        // V7.0 stays in production until the March-2027 switch.
        assertThat(SampleFiles.forTemplate("TPT")).extracting(SampleFiles.Sample::version)
                .containsExactly("V8.0", "V7.0");
        assertThat(SampleFiles.forTemplate("TPT", "V8.0")).get()
                .extracting(SampleFiles.Sample::resource)
                .isEqualTo("samples/tpt/v8/00_showcase.xlsx");
        assertThat(SampleFiles.forTemplate("tpt", "v7.0")).isPresent();
        assertThat(SampleFiles.forTemplate("TPT", "V6.0")).isEmpty();
    }

    @Test
    void everyDeclaredResourceIsOnTheClasspath() {
        for (SampleFiles.Sample s : SampleFiles.declared()) {
            assertThat(SampleFiles.forTemplate(s.templateId(), s.version()))
                    .as("%s %s sample resource %s is not on the classpath — check the "
                            + "<resources> include in web-app/pom.xml",
                            s.templateId(), s.version(), s.resource())
                    .isPresent();
        }
    }

    @Test
    void oneSamplePerTemplateVersionWithDistinctDownloadNames() {
        assertThat(SampleFiles.declared())
                .extracting(s -> s.templateId() + " " + s.version())
                .doesNotHaveDuplicates();
        assertThat(SampleFiles.declared()).extracting(SampleFiles.Sample::filename)
                .doesNotHaveDuplicates();
    }

    @Test
    void samplesPointAtTheShowcaseFixtures() {
        // Not the numbered fixtures: those demonstrate one rule each. See the
        // SampleFiles javadoc.
        for (SampleFiles.Sample s : SampleFiles.declared()) {
            assertThat(s.resource()).endsWith("/00_showcase.xlsx");
            assertThat(s.filename()).endsWith(".xlsx");
        }
    }

    @Test
    void lookupIsCaseInsensitiveAndRejectsUnknownTemplates() {
        assertThat(SampleFiles.forTemplate("tpt")).isNotEmpty();
        assertThat(SampleFiles.forTemplate("UFO")).isEmpty();
        assertThat(SampleFiles.forTemplate(null)).isEmpty();
        assertThat(SampleFiles.forTemplate("TPT", null)).isEmpty();
    }

    @Test
    void recognisesTheDownloadNameOfEverySample() {
        for (SampleFiles.Sample s : SampleFiles.declared()) {
            assertThat(SampleFiles.isSampleFilename(s.filename())).isTrue();
            assertThat(SampleFiles.isSampleFilename("C:\\Downloads\\" + s.filename().toUpperCase())).isTrue();
        }
        assertThat(SampleFiles.isSampleFilename("20260331_TPTV7_LU123.xlsx")).isFalse();
        assertThat(SampleFiles.isSampleFilename(null)).isFalse();
    }
}
