package com.findatex.validator.web.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.util.Locale;

/**
 * Sanitises a page-view beacon and hands it to {@link UsageStatsService},
 * which stores it as a {@code usage_event} row with {@code event_type =
 * 'page_view'} (the separate {@code page_view} table is legacy, see
 * docs/USAGE_STATS.md). Everything about persistence — inert without a DB,
 * async, retries — lives in the stats service now.
 *
 * <p>Why page views exist: {@code usage_event} runs alone cannot say whether a
 * quiet week means nobody came or everybody left without uploading. With the
 * views in the same table, the dashboard can build the funnel
 * page_view → validate → report_download per (daily-rotating) visitor hash.
 *
 * <p>Still deliberately not a general analytics tool: no cookie, no local
 * storage, no raw IP, no full referrer URL, no query strings. The referrer is
 * reduced to its <b>host</b> here and the raw URL is never stored or logged.
 */
@ApplicationScoped
public class PageViewService {

    /** Generous caps: these columns exist to be grouped by, not to hold prose. */
    private static final int MAX_PATH = 200;
    private static final int MAX_HOST = 120;
    private static final int MAX_CAMPAIGN = 64;

    @Inject
    UsageStatsService usageStats;

    public boolean enabled() {
        return usageStats.enabled();
    }

    /**
     * Records one page load. {@code referrer} may be a full URL — only its host
     * survives. Country / visitor hash / device come from {@code ctx}.
     */
    public void record(String path, String referrer, String campaign, ClientContext ctx) {
        if (!enabled()) return;
        usageStats.recordPageView(normalisePath(path), hostOf(referrer), normaliseCampaign(campaign), ctx);
    }

    /**
     * Path only: query string and fragment are cut off (they can carry search
     * terms or ids), and anything unreasonably long is truncated rather than
     * dropped so the view is still counted.
     */
    static String normalisePath(String raw) {
        if (raw == null || raw.isBlank()) return "/";
        String path = raw.trim();
        int cut = indexOfFirst(path, '?', '#');
        if (cut >= 0) path = path.substring(0, cut);
        if (path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        return path.length() > MAX_PATH ? path.substring(0, MAX_PATH) : path;
    }

    /**
     * Host of a referrer URL, lower-cased and without a leading {@code www.} so
     * {@code www.linkedin.com} and {@code linkedin.com} group together. Returns
     * {@code null} for anything unparseable — a referrer is a nice-to-have, and
     * a malformed one must never cost the page view.
     */
    static String hostOf(String referrer) {
        if (referrer == null || referrer.isBlank()) return null;
        try {
            URI uri = URI.create(referrer.trim());
            // Only real web referrers. android-app://, chrome-extension:// and
            // friends parse fine and would otherwise be recorded as "hosts"
            // that mean nothing in a traffic-source report.
            String scheme = uri.getScheme();
            if (scheme == null) return null;
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) return null;
            String host = uri.getHost();
            if (host == null || host.isBlank()) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            return host.length() > MAX_HOST ? host.substring(0, MAX_HOST) : host;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Campaign tag reduced to a safe slug. Anything outside
     * {@code [a-z0-9._-]} is dropped rather than escaped: this value is
     * grouped by and printed in reports, and it comes straight from a URL
     * anyone can craft.
     */
    static String normaliseCampaign(String raw) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : raw.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (sb.length() >= MAX_CAMPAIGN) break;
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static int indexOfFirst(String s, char a, char b) {
        int i = s.indexOf(a);
        int j = s.indexOf(b);
        if (i < 0) return j;
        if (j < 0) return i;
        return Math.min(i, j);
    }
}
