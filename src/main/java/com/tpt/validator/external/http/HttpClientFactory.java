package com.tpt.validator.external.http;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpClientFactory {

    private static volatile HttpClient instance;

    private HttpClientFactory() {}

    public static HttpClient get() {
        HttpClient local = instance;
        if (local == null) {
            synchronized (HttpClientFactory.class) {
                if (instance == null) instance = build();
                local = instance;
            }
        }
        return local;
    }

    public static synchronized void rebuild() { instance = null; }

    private static HttpClient build() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.getDefault())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }
}
