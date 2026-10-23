package com.findatex.validator.web.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class RateLimitServiceTest {

    @Inject
    RateLimitService rateLimits;

    @Test
    void inspectNeverCreatesABucket() {
        long before = rateLimits.trackedKeys();
        for (int i = 0; i < 500; i++) {
            RateLimitStatus s = rateLimits.inspect("198.18." + (i / 250) + "." + (i % 250));
            assertThat(s.remaining()).isEqualTo(s.limit());
        }
        assertThat(rateLimits.trackedKeys()).isEqualTo(before);
    }

    @Test
    void nonLiteralKeysShareOneBucket() {
        assertThat(rateLimits.normaliseKey("evil.example.com")).isEqualTo("unknown");
        assertThat(rateLimits.normaliseKey("x".repeat(8000))).isEqualTo("unknown");
        assertThat(rateLimits.normaliseKey(null)).isEqualTo("unknown");
        assertThat(rateLimits.normaliseKey(" 203.0.113.9 ")).isEqualTo("203.0.113.9");
    }

    @Test
    void bucketMapsAreBounded() {
        assertThat(RateLimitService.MAX_KEYS).isPositive();
        assertThat(RateLimitService.IDLE_EXPIRY).isGreaterThan(java.time.Duration.ofHours(1));
    }
}
