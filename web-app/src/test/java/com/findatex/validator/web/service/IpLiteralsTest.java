package com.findatex.validator.web.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IpLiteralsTest {

    @Test
    void acceptsIpv4AndIpv6Literals() {
        assertThat(IpLiterals.normalise("203.0.113.5")).contains("203.0.113.5");
        assertThat(IpLiterals.normalise(" 2001:db8::1 ")).contains("2001:db8:0:0:0:0:0:1");
        assertThat(IpLiterals.normalise("::ffff:198.51.100.1")).contains("198.51.100.1");
    }

    @Test
    void rejectsAnythingThatWouldNeedDns() {
        assertThat(IpLiterals.parse("evil.example.com")).isEmpty();
        assertThat(IpLiterals.parse("localhost")).isEmpty();
        assertThat(IpLiterals.parse("256.1.1.1")).isEmpty();
        assertThat(IpLiterals.parse("1.2.3.4:8080")).isEmpty();
        assertThat(IpLiterals.parse("[2001:db8::1]")).isEmpty();
        assertThat(IpLiterals.parse("deadbeef")).isEmpty();
        assertThat(IpLiterals.parse("")).isEmpty();
        assertThat(IpLiterals.parse(null)).isEmpty();
        assertThat(IpLiterals.parse("x".repeat(8000))).isEmpty();
    }

    @Test
    void ipv6RateLimitKeyCollapsesToSlash64() {
        assertThat(IpLiterals.rateLimitKey("2001:db8:1:2:aaaa::1"))
                .isEqualTo(IpLiterals.rateLimitKey("2001:db8:1:2:bbbb::2"))
                .contains("2001:db8:1:2::/64");
        assertThat(IpLiterals.rateLimitKey("2001:db8:1:3::1"))
                .isNotEqualTo(IpLiterals.rateLimitKey("2001:db8:1:2::1"));
        assertThat(IpLiterals.rateLimitKey("198.51.100.9")).contains("198.51.100.9");
    }

    @Test
    void resolveTakesTheNthEntryFromTheRight() {
        assertThat(ClientContextFactory.resolve("10.0.0.1", List.of("1.1.1.1, 203.0.113.5"), 1))
                .isEqualTo("203.0.113.5");
        assertThat(ClientContextFactory.resolve("10.0.0.1", List.of("1.1.1.1, 203.0.113.5, 35.191.0.1"), 2))
                .isEqualTo("203.0.113.5");
        // Repeated headers are one list, in order.
        assertThat(ClientContextFactory.resolve("10.0.0.1", List.of("1.1.1.1", "203.0.113.5"), 1))
                .isEqualTo("203.0.113.5");
    }

    @Test
    void resolveFallsBackToThePeer() {
        assertThat(ClientContextFactory.resolve("10.0.0.1", List.of(), 1)).isEqualTo("10.0.0.1");
        assertThat(ClientContextFactory.resolve("10.0.0.1", List.of("203.0.113.5"), 2)).isEqualTo("10.0.0.1");
        assertThat(ClientContextFactory.resolve("10.0.0.1", List.of("1.1.1.1, evil.example"), 1))
                .isEqualTo("10.0.0.1");
        assertThat(ClientContextFactory.resolve("10.0.0.1", List.of("203.0.113.5"), 0)).isEqualTo("10.0.0.1");
    }
}
