package com.findatex.validator.external.http;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Which user-configurable endpoints the desktop may post to (usage stats,
 * newsletter, quick feedback). The settings file is editable, and those posts
 * carry the usage token or an e-mail address — so only {@code https}, plus plain
 * {@code http} to the loopback interface for local development and tests.
 */
public final class EndpointPolicy {

    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private EndpointPolicy() {
    }

    public static boolean isAllowed(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isEmpty() || uri.getUserInfo() != null) return false;
            if ("https".equals(scheme)) return true;
            return "http".equals(scheme) && LOOPBACK.contains(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
