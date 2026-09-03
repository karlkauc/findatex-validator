package com.findatex.validator.web.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the sanitising seams of {@link PageViewService}. No DB or CDI
 * needed (persistence and retries live in {@code UsageStatsService}). The sanitisers matter twice over: they enforce the privacy
 * promise (no query strings, no full referrer URLs) and they keep values that
 * come straight out of an attacker-controllable URL from reaching a report.
 */
class PageViewServiceTest {

    // --- path ---------------------------------------------------------------

    @Test
    void pathKeepsTheRouteAndDropsQueryAndFragment() {
        // A query string can carry a search term or an id — never stored.
        assertThat(PageViewService.normalisePath("/rules/tpt-v8-0?q=secret")).isEqualTo("/rules/tpt-v8-0");
        assertThat(PageViewService.normalisePath("/findings#row-42")).isEqualTo("/findings");
        assertThat(PageViewService.normalisePath("/")).isEqualTo("/");
    }

    @Test
    void pathFallsBackToRootAndIsAlwaysAbsolute() {
        assertThat(PageViewService.normalisePath(null)).isEqualTo("/");
        assertThat(PageViewService.normalisePath("   ")).isEqualTo("/");
        assertThat(PageViewService.normalisePath("?only=query")).isEqualTo("/");
        assertThat(PageViewService.normalisePath("rules/tpt")).isEqualTo("/rules/tpt");
    }

    @Test
    void anAbsurdlyLongPathIsTruncatedNotDropped() {
        String path = "/" + "a".repeat(500);
        String stored = PageViewService.normalisePath(path);
        assertThat(stored).hasSize(200).startsWith("/aaa");
    }

    // --- referrer -----------------------------------------------------------

    @Test
    void onlyTheReferrerHostSurvivesAndWwwIsStripped() {
        // The path of the referring page is itself potentially personal data.
        assertThat(PageViewService.hostOf("https://www.linkedin.com/feed/update/12345"))
                .isEqualTo("linkedin.com");
        assertThat(PageViewService.hostOf("https://www.google.com/search?q=tpt+validator"))
                .isEqualTo("google.com");
    }

    @Test
    void anUnparseableReferrerCostsTheReferrerNotTheView() {
        assertThat(PageViewService.hostOf(null)).isNull();
        assertThat(PageViewService.hostOf("")).isNull();
        assertThat(PageViewService.hostOf("not a url")).isNull();
        assertThat(PageViewService.hostOf("android-app://com.example")).isNull();
    }

    // --- campaign -----------------------------------------------------------

    @Test
    void campaignIsReducedToASafeSlug() {
        assertThat(PageViewService.normaliseCampaign("LinkedIn-Post_2026.08")).isEqualTo("linkedin-post_2026.08");
        // Comes from a URL anyone can craft and ends up in a report.
        assertThat(PageViewService.normaliseCampaign("drop table page_view;--")).isEqualTo("droptablepage_view--");
        assertThat(PageViewService.normaliseCampaign("<script>alert(1)</script>")).isEqualTo("scriptalert1script");
    }

    @Test
    void campaignIsNullWhenAbsentOrEntirelyStripped() {
        assertThat(PageViewService.normaliseCampaign(null)).isNull();
        assertThat(PageViewService.normaliseCampaign("   ")).isNull();
        assertThat(PageViewService.normaliseCampaign("=== ***")).isNull();
    }

    @Test
    void campaignIsCapped() {
        assertThat(PageViewService.normaliseCampaign("x".repeat(200))).hasSize(64);
    }

}
