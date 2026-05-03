package com.findatex.validator.ui.notification;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FormattersTest {

    @Test
    void humanBytes_zero() {
        assertThat(Formatters.humanBytes(0)).isEqualTo("0 B");
    }

    @Test
    void humanBytes_negativeFallsBackToZero() {
        assertThat(Formatters.humanBytes(-1)).isEqualTo("0 B");
    }

    @Test
    void humanBytes_subKilobyte() {
        assertThat(Formatters.humanBytes(642)).isEqualTo("642 B");
        assertThat(Formatters.humanBytes(1023)).isEqualTo("1023 B");
    }

    @Test
    void humanBytes_kilobyteBoundary() {
        assertThat(Formatters.humanBytes(1024)).isEqualTo("1.0 KB");
        assertThat(Formatters.humanBytes(1536)).isEqualTo("1.5 KB");
    }

    @Test
    void humanBytes_megabyte() {
        assertThat(Formatters.humanBytes(1024L * 1024L)).isEqualTo("1.0 MB");
        assertThat(Formatters.humanBytes((long) (2.4 * 1024 * 1024))).isEqualTo("2.4 MB");
    }

    @Test
    void humanBytes_gigabyte() {
        assertThat(Formatters.humanBytes(1024L * 1024L * 1024L)).isEqualTo("1.0 GB");
        assertThat(Formatters.humanBytes((long) (1.1 * 1024 * 1024 * 1024))).isEqualTo("1.1 GB");
    }

    @Test
    void humanDuration_nullOrZero() {
        assertThat(Formatters.humanDuration(null)).isEqualTo("0 ms");
        assertThat(Formatters.humanDuration(Duration.ZERO)).isEqualTo("0 ms");
        assertThat(Formatters.humanDuration(Duration.ofMillis(-5))).isEqualTo("0 ms");
    }

    @Test
    void humanDuration_subSecond() {
        assertThat(Formatters.humanDuration(Duration.ofMillis(1))).isEqualTo("1 ms");
        assertThat(Formatters.humanDuration(Duration.ofMillis(850))).isEqualTo("850 ms");
        assertThat(Formatters.humanDuration(Duration.ofMillis(999))).isEqualTo("999 ms");
    }

    @Test
    void humanDuration_seconds() {
        assertThat(Formatters.humanDuration(Duration.ofMillis(1000))).isEqualTo("1.0 s");
        assertThat(Formatters.humanDuration(Duration.ofMillis(1234))).isEqualTo("1.2 s");
        assertThat(Formatters.humanDuration(Duration.ofMillis(59_900))).isEqualTo("59.9 s");
    }

    @Test
    void humanDuration_minutesAndSeconds() {
        assertThat(Formatters.humanDuration(Duration.ofSeconds(60))).isEqualTo("1 min 0 s");
        assertThat(Formatters.humanDuration(Duration.ofSeconds(133))).isEqualTo("2 min 13 s");
        assertThat(Formatters.humanDuration(Duration.ofMinutes(65))).isEqualTo("65 min 0 s");
    }
}
