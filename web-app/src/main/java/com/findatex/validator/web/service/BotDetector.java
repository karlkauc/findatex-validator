package com.findatex.validator.web.service;

import java.util.List;
import java.util.Locale;

/**
 * User-Agent heuristic for dropping non-human page views.
 *
 * <p>The page-view beacon is fired from JavaScript, which already excludes most
 * crawlers — but not the ones that render (Googlebot, Bingbot), not headless
 * browsers, and not uptime monitors. At the traffic level this project is
 * measuring, a handful of daily bot hits would be a large share of the number
 * and would make the "visitors vs. validations" ratio meaningless.
 *
 * <p>Deliberately a substring allow-list of well-known markers rather than
 * anything clever: false negatives just leave a bot in the count, and the
 * alternative (fingerprinting the client to prove it is human) is exactly what
 * this project does not do.
 */
public final class BotDetector {

    /** Lower-cased substrings; matched against the raw User-Agent. */
    private static final List<String> MARKERS = List.of(
            "bot", "crawl", "spider", "slurp", "scrape",
            "headlesschrome", "phantomjs", "puppeteer", "playwright", "selenium",
            "curl/", "wget/", "python-requests", "python-urllib", "httpclient",
            "go-http-client", "java/", "okhttp", "axios/", "node-fetch",
            "facebookexternalhit", "whatsapp", "telegrambot", "slackbot",
            "discordbot", "linkedinbot", "embedly", "quora link preview",
            "pingdom", "uptimerobot", "statuscake", "site24x7", "lighthouse",
            "monitoring", "preview", "validator", "feedfetcher",
            // HTTP libraries beyond requests/urllib (python-httpx, python-aiohttp,
            // Perl, Apache/Java HTTP clients) and API tools
            "python-", "httpx", "aiohttp", "libwww-perl", "apache-httpclient",
            "java-http-client", "postmanruntime", "insomnia",
            // load-testing tools
            "k6/", "locust", "jmeter", "artillery", "gatling",
            // internet-wide scanners and crawlers that announce themselves
            "zgrab", "masscan", "nuclei", "censys", "palo alto",
            "wordpresspostchecker", "dataprovider",
            // Frozen fake UA (iOS 13.2.3, 2019) sent by scanner fleets; a real
            // device still on that exact build is negligible by now.
            "iphone os 13_2_3 like");

    private BotDetector() {
    }

    /**
     * True when the User-Agent looks automated. A missing or empty UA counts as
     * a bot: every real browser sends one, and a beacon without it is either a
     * script or a privacy tool that would not run our JS anyway.
     */
    public static boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return true;
        String ua = userAgent.toLowerCase(Locale.ROOT);
        for (String marker : MARKERS) {
            if (ua.contains(marker)) return true;
        }
        return false;
    }
}
