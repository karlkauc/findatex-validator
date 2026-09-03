package com.findatex.validator.web.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VisitorHasherTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 3);
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0) Chrome/141";

    @Test
    void sameVisitorSameDaySameHashAcrossInstancesWhenSecretIsShared() {
        VisitorHasher a = new VisitorHasher(Optional.of("shared-secret"));
        VisitorHasher b = new VisitorHasher(Optional.of("shared-secret"));
        String h1 = a.hash("203.0.113.9", UA, DAY);
        assertThat(h1).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(b.hash("203.0.113.9", UA, DAY)).isEqualTo(h1);
    }

    @Test
    void rotatesDailyAndSeparatesVisitors() {
        VisitorHasher h = new VisitorHasher(Optional.of("s"));
        String today = h.hash("203.0.113.9", UA, DAY);
        assertThat(h.hash("203.0.113.9", UA, DAY.plusDays(1))).isNotEqualTo(today);
        assertThat(h.hash("203.0.113.10", UA, DAY)).isNotEqualTo(today);
        assertThat(h.hash("203.0.113.9", UA + " Safari", DAY)).isNotEqualTo(today);
    }

    @Test
    void differentSecretsDifferentHashes() {
        String a = new VisitorHasher(Optional.of("a")).hash("203.0.113.9", UA, DAY);
        String b = new VisitorHasher(Optional.of("b")).hash("203.0.113.9", UA, DAY);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void withoutSecretStillHashesConsistentlyWithinTheProcess() {
        VisitorHasher h = new VisitorHasher(Optional.empty());
        assertThat(h.hash("203.0.113.9", UA, DAY)).isEqualTo(h.hash("203.0.113.9", UA, DAY));
        // ...but two processes disagree (random salt) — the documented "approximate" mode.
        assertThat(new VisitorHasher(Optional.empty()).hash("203.0.113.9", UA, DAY))
                .isNotEqualTo(h.hash("203.0.113.9", UA, DAY));
    }

    @Test
    void neverContainsTheIpAndIsNullWithoutOne() {
        VisitorHasher h = new VisitorHasher(Optional.of("s"));
        assertThat(h.hash("203.0.113.9", UA, DAY)).doesNotContain("203").doesNotContain("113");
        assertThat(h.hash(null, UA, DAY)).isNull();
        assertThat(h.hash("  ", UA, DAY)).isNull();
        assertThat(h.hash("203.0.113.9", null, DAY)).isNotNull();
    }
}
