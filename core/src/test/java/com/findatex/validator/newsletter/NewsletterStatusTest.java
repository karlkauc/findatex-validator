package com.findatex.validator.newsletter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsletterStatusTest {

    @Test
    void wireIsLowercaseName() {
        assertThat(NewsletterStatus.ALREADY_PENDING.wire()).isEqualTo("already_pending");
        assertThat(NewsletterStatus.PENDING.wire()).isEqualTo("pending");
    }

    @Test
    void fromWireRoundTrips() {
        for (NewsletterStatus s : NewsletterStatus.values()) {
            assertThat(NewsletterStatus.fromWire(s.wire())).isEqualTo(s);
        }
    }

    @Test
    void fromWireDefaultsToUnavailable() {
        assertThat(NewsletterStatus.fromWire(null)).isEqualTo(NewsletterStatus.UNAVAILABLE);
        assertThat(NewsletterStatus.fromWire("garbage")).isEqualTo(NewsletterStatus.UNAVAILABLE);
        assertThat(NewsletterStatus.fromWire(" PENDING ")).isEqualTo(NewsletterStatus.PENDING);
    }
}
