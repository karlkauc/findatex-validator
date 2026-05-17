package com.findatex.validator.feedback;

import com.findatex.validator.feedback.GitHubIssueLink.FalsePositiveReport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubIssueLinkTest {

    private static FalsePositiveReport sample(String comment, String message) {
        return new FalsePositiveReport(
                "TPT", "V7", "ERROR", "PRESENCE/TPT-F1", "INSURANCE_PRIIPS",
                "12", "Portfolio Currency", "XYZ", message,
                "LU000", "Some Fund", "2026-03-31", "DE000", "Some Bond",
                "0.1234", "1.0.0", comment);
    }

    @Test
    void slugValidation_acceptsOwnerRepo() {
        assertThat(GitHubIssueLink.isValidRepoSlug("karlkauc/findatex-validator")).isTrue();
        assertThat(GitHubIssueLink.isValidRepoSlug("a.b_c/d.e_f")).isTrue();
    }

    @Test
    void slugValidation_rejectsBadInput() {
        assertThat(GitHubIssueLink.isValidRepoSlug(null)).isFalse();
        assertThat(GitHubIssueLink.isValidRepoSlug("")).isFalse();
        assertThat(GitHubIssueLink.isValidRepoSlug("noslash")).isFalse();
        assertThat(GitHubIssueLink.isValidRepoSlug("a/b/c")).isFalse();
        assertThat(GitHubIssueLink.isValidRepoSlug("own er/repo")).isFalse();
        assertThat(GitHubIssueLink.isValidRepoSlug("../etc/passwd")).isFalse();
        assertThat(GitHubIssueLink.isValidRepoSlug("https://github.com/x/y")).isFalse();
    }

    @Test
    void title_includesRuleAndField() {
        assertThat(GitHubIssueLink.issueTitle(sample("c", "m")))
                .isEqualTo("[False positive] PRESENCE/TPT-F1 · field 12 Portfolio Currency");
    }

    @Test
    void body_containsAllContextAndComment() {
        String body = GitHubIssueLink.issueBody(sample("This field is optional for my profile", "bad value"));
        assertThat(body)
                .contains("This field is optional for my profile")
                .contains("TPT — V7")
                .contains("PRESENCE/TPT-F1")
                .contains("INSURANCE_PRIIPS")
                .contains("LU000 — Some Fund")
                .contains("DE000 — Some Bond")
                .contains("bad value")
                .contains("publishes the values above on GitHub");
    }

    @Test
    void url_isWellFormedAndEncoded() {
        String url = GitHubIssueLink.issueUrl("karlkauc/findatex-validator",
                sample("needs space & symbols", "line1\nline2"));
        assertThat(url).startsWith(
                "https://github.com/karlkauc/findatex-validator/issues/new?labels=false-positive");
        assertThat(url).contains("&title=").contains("&body=");
        assertThat(url).doesNotContain(" ");      // spaces encoded
        assertThat(url).doesNotContain("\n");      // newlines encoded
        assertThat(url).contains("%0A");           // encoded newline present in body
    }

    @Test
    void url_rejectsInvalidSlug() {
        assertThatThrownBy(() -> GitHubIssueLink.issueUrl("bogus", sample("c", "m")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void url_isCappedByTruncatingFreeText() {
        String hugeComment = "x".repeat(20_000);
        String hugeMessage = "y".repeat(20_000);
        String url = GitHubIssueLink.issueUrl("o/r", sample(hugeComment, hugeMessage));
        assertThat(url.length()).isLessThanOrEqualTo(GitHubIssueLink.MAX_URL_LENGTH);
        assertThat(url).startsWith("https://github.com/o/r/issues/new");
    }

    @Test
    void nullFieldsRenderAsDash() {
        FalsePositiveReport r = new FalsePositiveReport(
                "EET", "V1.1.3", "WARNING", "FORMAT/EET-F2", null,
                null, null, null, "msg", null, null, null, null, null, null, null, null);
        String body = GitHubIssueLink.issueBody(r);
        assertThat(body).contains("| Profile | — |");
        assertThat(GitHubIssueLink.issueTitle(r)).isEqualTo("[False positive] FORMAT/EET-F2");
    }
}
