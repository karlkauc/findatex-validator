package com.findatex.validator.external.http;

/**
 * @deprecated The validator now uses {@link java.net.HttpURLConnection} via {@link HttpExecutor}
 *             so that NTLM proxy auth works (the legacy client supports it; java.net.http.HttpClient
 *             does not — JDK-8266421). This class is kept as a no-op stub so callers that still
 *             invoke {@link #rebuild()} compile.
 */
@Deprecated
public final class HttpClientFactory {
    private HttpClientFactory() {}
    @Deprecated public static void rebuild() { /* no-op */ }
}
