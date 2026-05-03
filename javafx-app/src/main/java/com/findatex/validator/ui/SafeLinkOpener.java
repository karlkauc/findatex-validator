package com.findatex.validator.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;
import java.util.Set;

/**
 * Hands a URL to the OS browser only when its scheme is on a small allowlist.
 * Used by {@link AboutDialog} and {@link HelpDialog} so a future change that
 * starts pulling Markdown from generated/spec sources cannot accidentally
 * forward {@code javascript:}, {@code file:}, {@code vbscript:}, or
 * {@code data:} URIs to the OS handler — those launch arbitrary registered
 * applications on most desktops and would otherwise be a one-line RCE vector.
 */
public final class SafeLinkOpener {

    private static final Logger log = LoggerFactory.getLogger(SafeLinkOpener.class);

    /** Schemes safe to hand to {@code Desktop.browse(...)}. */
    private static final Set<String> ALLOWED = Set.of("http", "https", "mailto");

    private SafeLinkOpener() {}

    public static void open(String url) {
        if (url == null || url.isBlank()) return;
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            log.debug("Refusing malformed URL: {}", url);
            return;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED.contains(scheme.toLowerCase())) {
            log.warn("Refusing to open URL with disallowed scheme: {}", scheme);
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) return;
            Desktop d = Desktop.getDesktop();
            if (d.isSupported(Desktop.Action.BROWSE)) d.browse(uri);
        } catch (Exception e) {
            log.debug("Could not open {}: {}", uri, e.toString());
        }
    }
}
