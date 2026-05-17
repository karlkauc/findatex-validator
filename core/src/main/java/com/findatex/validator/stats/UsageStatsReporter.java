package com.findatex.validator.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.findatex.validator.config.AppSettings;
import com.findatex.validator.config.SettingsService;
import com.findatex.validator.external.http.HttpExecutor;
import com.findatex.validator.external.http.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

/**
 * Fire-and-forget background sender for {@link UsageEvent}s. {@link #report} only
 * enqueues and returns immediately, so it never affects the measured validation
 * runtime or the UI. A single daemon worker drains a bounded queue; on overflow
 * events are silently dropped. Every transport failure/timeout is swallowed and
 * logged at DEBUG only — the caller is never disturbed.
 *
 * <p>The shared {@link HttpExecutor} keeps its built-in 10s/30s timeouts and
 * 3-attempt backoff; a dead endpoint therefore ties up only this one daemon
 * thread (never the UI), bounded by the queue.
 */
public final class UsageStatsReporter {

    private static final Logger log = LoggerFactory.getLogger(UsageStatsReporter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int QUEUE_CAPACITY = 100;

    private static volatile UsageStatsReporter instance;

    private final HttpExecutor http;
    private final Supplier<AppSettings.UsageStats> cfg;
    private final String ingestToken;
    private final BlockingQueue<Pending> queue;

    private record Pending(String endpoint, String token, String json) {}

    UsageStatsReporter(HttpExecutor http,
                       Supplier<AppSettings.UsageStats> cfg,
                       String ingestToken,
                       int queueCapacity) {
        this.http = http;
        this.cfg = cfg;
        this.ingestToken = ingestToken == null ? "" : ingestToken;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        Thread worker = new Thread(this::drain, "usage-stats-sender");
        worker.setDaemon(true);
        worker.start();
    }

    public static UsageStatsReporter getInstance() {
        UsageStatsReporter local = instance;
        if (local == null) {
            synchronized (UsageStatsReporter.class) {
                if (instance == null) {
                    String token = System.getenv("FINDATEX_USAGE_TOKEN");
                    instance = new UsageStatsReporter(
                            new HttpExecutor(new RateLimiter(5, 5)),
                            () -> SettingsService.getInstance().getCurrent().usageStats(),
                            token == null ? "" : token,
                            QUEUE_CAPACITY);
                }
                local = instance;
            }
        }
        return local;
    }

    /** Non-blocking: validates the gate, serialises, and enqueues. Never throws. */
    public void report(UsageEvent event) {
        try {
            AppSettings.UsageStats c = cfg.get();
            if (c == null || !c.enabled()) return;
            String endpoint = c.endpointUrl();
            if (endpoint == null || endpoint.isBlank()) return;
            if (ingestToken.isBlank()) return;
            String json = MAPPER.writeValueAsString(event);
            if (!queue.offer(new Pending(endpoint.trim(), ingestToken, json))) {
                log.debug("Usage-stats queue full; dropping event");
            }
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.debug("Usage-stats enqueue failed (ignored): {}", e.toString());
        }
    }

    private void drain() {
        while (true) {
            Pending p;
            try {
                p = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                http.send(HttpExecutor.Request.post(
                        URI.create(p.endpoint()),
                        Map.of("Content-Type", "application/json",
                                "X-Usage-Token", p.token()),
                        p.json()));
            } catch (RuntimeException e) {
                log.debug("Usage-stats send failed (ignored): {}", e.toString());
            }
        }
    }
}
