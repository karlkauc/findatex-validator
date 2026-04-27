package com.tpt.validator.external.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Wraps {@link HttpURLConnection} with a token-bucket rate limit and
 * exponential-backoff retry on 429/5xx. We use HttpURLConnection (legacy)
 * rather than {@link java.net.http.HttpClient} because only the legacy
 * client supports NTLM proxy authentication via {@link java.net.Authenticator}.
 */
public final class HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpExecutor.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    public record Request(String method, URI uri, Map<String, String> headers, String body) {
        public static Request get(URI uri) { return new Request("GET", uri, Map.of(), null); }
        public static Request get(URI uri, Map<String, String> headers) { return new Request("GET", uri, headers, null); }
        public static Request post(URI uri, Map<String, String> headers, String body) { return new Request("POST", uri, headers, body); }
    }

    public record Response(int statusCode, String body) {}

    private final RateLimiter limiter;
    private final long backoffMs;

    public HttpExecutor(RateLimiter limiter) { this(limiter, 2_000L); }

    HttpExecutor(RateLimiter limiter, long backoffMs) {
        this.limiter = limiter;
        this.backoffMs = backoffMs;
    }

    public Optional<Response> send(Request req) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            limiter.acquire();
            try {
                Response r = executeOnce(req);
                if (r.statusCode() == 429 || r.statusCode() / 100 == 5) {
                    log.debug("HTTP {} from {} (attempt {}/{}), backing off",
                            r.statusCode(), req.uri(), attempt, MAX_ATTEMPTS);
                    if (attempt < MAX_ATTEMPTS) sleep(backoffMs * (1L << (attempt - 1)));
                    continue;
                }
                return Optional.of(r);
            } catch (IOException e) {
                log.debug("HTTP error to {} (attempt {}/{}): {}",
                        req.uri(), attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) sleep(backoffMs * (1L << (attempt - 1)));
            }
        }
        log.warn("Giving up on {} after {} attempts", req.uri(), MAX_ATTEMPTS);
        return Optional.empty();
    }

    private Response executeOnce(Request req) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) req.uri().toURL().openConnection();
        try {
            conn.setRequestMethod(req.method());
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            for (Map.Entry<String, String> h : req.headers().entrySet()) {
                conn.setRequestProperty(h.getKey(), h.getValue());
            }
            if ("POST".equals(req.method()) || "PUT".equals(req.method()) || "PATCH".equals(req.method())) {
                conn.setDoOutput(true);
                if (req.body() != null) {
                    byte[] bytes = req.body().getBytes(StandardCharsets.UTF_8);
                    if (!conn.getRequestProperties().containsKey("Content-Length")) {
                        conn.setFixedLengthStreamingMode(bytes.length);
                    }
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(bytes);
                    }
                }
            }
            int status = conn.getResponseCode();
            InputStream is = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
            String body = is == null ? "" : readAll(is);
            return new Response(status, body);
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Convenience: keeps a stable insertion-ordered map for header builders. */
    public static Map<String, String> headers(String... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("headers() needs key/value pairs");
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
