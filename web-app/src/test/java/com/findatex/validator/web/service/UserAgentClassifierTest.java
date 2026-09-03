package com.findatex.validator.web.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentClassifierTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/141.0 Safari/537.36 | desktop | Windows",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 Version/17.5 Safari/605.1.15 | desktop | Mac",
            "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0 | desktop | Linux",
            "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 Chrome/120 Safari/537.36 | desktop | Linux",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1 | mobile | iOS",
            "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1 | mobile | iOS",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/141.0 Mobile Safari/537.36 | mobile | Android",
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html) | bot | Other",
            "curl/8.5.0 | bot | Other",
            "Mozilla/5.0 (PlayStation; PlayStation 5/8.20) AppleWebKit/605.1.15 | desktop | Other",
    })
    void classifies(String ua, String device, String os) {
        assertThat(UserAgentClassifier.device(ua)).isEqualTo(device);
        assertThat(UserAgentClassifier.osFamily(ua)).isEqualTo(os);
    }

    @ParameterizedTest
    @CsvSource(value = {"null", "''", "'   '"}, nullValues = "null")
    void blankUaIsUnknownWithNoOs(String ua) {
        assertThat(UserAgentClassifier.device(ua)).isEqualTo("unknown");
        assertThat(UserAgentClassifier.osFamily(ua)).isNull();
        assertThat(UserAgentClassifier.truncate(ua)).isNull();
    }

    @ParameterizedTest
    @CsvSource({"10,10", "255,255", "300,255"})
    void truncatesToTheColumnCap(int len, int expected) {
        assertThat(UserAgentClassifier.truncate("x".repeat(len))).hasSize(expected);
    }
}
