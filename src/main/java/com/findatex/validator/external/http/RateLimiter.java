package com.findatex.validator.external.http;

public final class RateLimiter {

    private final double permitsPerNano;
    private final double burst;
    private double tokens;
    private long lastNanos;

    public RateLimiter(double permitsPerSec, double burst) {
        this.permitsPerNano = permitsPerSec / 1_000_000_000.0;
        this.burst = burst;
        this.tokens = burst;
        this.lastNanos = System.nanoTime();
    }

    public synchronized void acquire() {
        while (true) {
            long now = System.nanoTime();
            tokens = Math.min(burst, tokens + (now - lastNanos) * permitsPerNano);
            lastNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return;
            }
            long waitNanos = (long) ((1.0 - tokens) / permitsPerNano);
            try {
                Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
