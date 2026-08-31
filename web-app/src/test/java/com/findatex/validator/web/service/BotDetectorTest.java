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
    void aMissingUserAgentCountsAsABot() {
        // Every real browser sends one; a beacon without it is a script.
        assertThat(BotDetector.isBot(null)).isTrue();
        assertThat(BotDetector.isBot("")).isTrue();
        assertThat(BotDetector.isBot("   ")).isTrue();
    }
}
