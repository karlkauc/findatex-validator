package com.findatex.validator.external.http;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void firstNTokensAreInstant() {
        RateLimiter r = new RateLimiter(5, 5);
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) r.acquire();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isLessThan(50);
    }

    @Test
    void exceedingBurstThrottles() {
        RateLimiter r = new RateLimiter(10, 2);
        long start = System.nanoTime();
        for (int i = 0; i < 4; i++) r.acquire(); // 2 free, 2 must wait ~200ms total
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isBetween(150L, 600L);
    }
}
