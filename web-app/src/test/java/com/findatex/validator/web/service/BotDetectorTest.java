package com.findatex.validator.web.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The page-view count is only worth having if it counts people. At a few
 * visitors a day, a single crawler that renders JavaScript would dominate the
 * number and make the visitors-to-validations ratio meaningless.
 */
class BotDetectorTest {

    @Test
    void realBrowsersAreNotBots() {
        assertThat(BotDetector.isBot(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/141.0.0.0 Safari/537.36")).isFalse();
        assertThat(BotDetector.isBot(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                        + "Version/18.3 Safari/605.1.15")).isFalse();
        assertThat(BotDetector.isBot(
                "Mozilla/5.0 (X11; Linux x86_64; rv:135.0) Gecko/20100101 Firefox/135.0")).isFalse();
        assertThat(BotDetector.isBot(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1")).isFalse();
    }

    @Test
    void crawlersThatRenderJavaScriptAreDropped() {
        assertThat(BotDetector.isBot(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")).isTrue();
        assertThat(BotDetector.isBot(
                "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)")).isTrue();
        assertThat(BotDetector.isBot("Mozilla/5.0 (compatible; AhrefsBot/7.0)")).isTrue();
    }

    @Test
    void linkPreviewFetchersAndMonitorsAreDropped() {
        assertThat(BotDetector.isBot("facebookexternalhit/1.1")).isTrue();
        assertThat(BotDetector.isBot("LinkedInBot/1.0 (compatible; Mozilla/5.0)")).isTrue();
        assertThat(BotDetector.isBot("Slackbot-LinkExpanding 1.0")).isTrue();
        assertThat(BotDetector.isBot("Mozilla/5.0 (compatible; UptimeRobot/2.0)")).isTrue();
    }

    @Test
    void scriptedClientsAndHeadlessBrowsersAreDropped() {
        assertThat(BotDetector.isBot("curl/8.5.0")).isTrue();
        assertThat(BotDetector.isBot("python-requests/2.32.3")).isTrue();
        assertThat(BotDetector.isBot("Java/21.0.5")).isTrue();
        assertThat(BotDetector.isBot(
                "Mozilla/5.0 (X11; Linux x86_64) HeadlessChrome/141.0.0.0 Safari/537.36")).isTrue();
    }

    @Test
    void httpLibrariesLoadTestersAndScannersAreDropped() {
        for (String ua : new String[] {
                "python-httpx/0.27.2",
                "Python/3.12 aiohttp/3.10.5",
                "libwww-perl/6.77",
                "Apache-HttpClient/4.5.14 (Java/17.0.12)",
                "Java-http-client/21.0.5",
                "PostmanRuntime/7.42.0",
                "insomnia/2023.5.8",
                "k6/0.54.0 (https://k6.io/)",
                "Mozilla/5.0 (compatible; Locust/2.31.8)",
                "Apache-JMeter/5.6.3",
                "Artillery (https://artillery.io)",
                "gatling",
                "Mozilla/5.0 zgrab/0.x",
                "masscan/1.3 (https://github.com/robertdavidgraham/masscan)",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Nuclei - Open-source project",
                "Mozilla/5.0 (compatible; CensysInspect/1.1; +https://about.censys.io/)",
                "Expanse, a Palo Alto Networks company, searches across the global IPv4 space",
                "WordPressPostChecker",
                "Mozilla/5.0 (compatible; Dataprovider.com)",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"}) {
            assertThat(BotDetector.isBot(ua)).as(ua).isTrue();
        }
    }

    @Test
    void newMarkersLeaveCurrentBrowsersAlone() {
        assertThat(BotDetector.isBot(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0")).isFalse();
        assertThat(BotDetector.isBot(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")).isFalse();
        assertThat(BotDetector.isBot(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6_1 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1")).isFalse();
        assertThat(BotDetector.isBot(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/141.0.0.0 Mobile Safari/537.36")).isFalse();
    }

    @Test
    void aMissingUserAgentCountsAsABot() {
        // Every real browser sends one; a beacon without it is a script.
        assertThat(BotDetector.isBot(null)).isTrue();
        assertThat(BotDetector.isBot("")).isTrue();
        assertThat(BotDetector.isBot("   ")).isTrue();
    }
}
