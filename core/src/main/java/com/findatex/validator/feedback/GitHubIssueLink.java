package com.findatex.validator.feedback;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds a GitHub "New Issue" pre-filled URL for reporting a false-positive
 * validation finding. No network access and no token: the URL is opened in the
 * user's browser and they press <em>Submit</em> themselves while logged in to
 * GitHub. Shared by the JavaFX desktop app and the Quarkus/React web app so
 * both produce byte-identical issues.
 *
 * <p>The TypeScript mirror lives at
 * {@code web-app/src/main/frontend/src/feedback/githubIssue.ts} — keep the two
 * in sync (title format, body layout, truncation behaviour).
 */
public final class GitHubIssueLink {

    private GitHubIssueLink() {}

    /** GitHub's pre-fill is dropped past ~8 KB; stay comfortably under. */
    static final int MAX_URL_LENGTH = 7000;
    private static final String TRUNCATION_MARKER = " …[truncated]";

    /**
     * Flat carrier for one reported finding. Built from a {@code Finding}
     * (desktop) or a {@code FindingDto} (web) at the call site. Any field may
     * be {@code null}/blank; the body renders those as {@code —}.
     */
    public record FalsePositiveReport(
            String templateId,
            String templateVersion,
            String severity,
            String ruleId,
            String profile,
            String fieldNum,
            String fieldName,
            String value,
            String message,
            String portfolioId,
            String portfolioName,
            String valuationDate,
            String instrumentCode,
            String instrumentName,
            String valuationWeight,
            String appVersion,
            String userComment) {

        FalsePositiveReport withCommentAndMessage(String comment, String msg) {
            return new FalsePositiveReport(templateId, templateVersion, severity, ruleId,
                    profile, fieldNum, fieldName, value, msg, portfolioId, portfolioName,
                    valuationDate, instrumentCode, instrumentName, valuationWeight,
                    appVersion, comment);
        }
    }

    /**
     * Strict {@code owner/repo} check: exactly one slash, each segment made of
     * GitHub-legal characters, no path traversal, no scheme.
     */
    public static boolean isValidRepoSlug(String slug) {
        if (slug == null) return false;
        String s = slug.trim();
        if (s.isEmpty() || s.contains("..")) return false;
        return s.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+");
    }

    public static String issueTitle(FalsePositiveReport r) {
        StringBuilder t = new StringBuilder("[False positive] ").append(blankToDash(r.ruleId()));
        if (notBlank(r.fieldNum()) || notBlank(r.fieldName())) {
            t.append(" · field ").append(blankToDash(r.fieldNum()));
            if (notBlank(r.fieldName())) t.append(' ').append(r.fieldName());
        }
        return t.toString();
    }

    public static String issueBody(FalsePositiveReport r) {
        StringBuilder b = new StringBuilder();
        b.append("**Why this is a false positive**\n\n");
        b.append(notBlank(r.userComment()) ? r.userComment().trim()
                : "_(no explanation provided — please describe why this finding is wrong)_");
        b.append("\n\n---\n\n");
        b.append("**Finding context** (auto-filled from the validator)\n\n");
        b.append("| Field | Value |\n|---|---|\n");
        row(b, "Template", join(r.templateId(), r.templateVersion()));
        row(b, "Severity", r.severity());
        row(b, "Rule", r.ruleId());
        row(b, "Profile", r.profile());
        row(b, "Field", join(r.fieldNum(), r.fieldName()));
        row(b, "Reported value", r.value());
        row(b, "Message", r.message());
        row(b, "Fund", join(r.portfolioId(), r.portfolioName()));
        row(b, "Valuation date", r.valuationDate());
        row(b, "Instrument", join(r.instrumentCode(), r.instrumentName()));
        row(b, "Weight", r.valuationWeight());
        row(b, "App version", r.appVersion());
        b.append("\n> ⚠️ Submitting this issue publishes the values above on GitHub. ")
                .append("Remove or redact anything confidential before pressing **Submit**.");
        return b.toString();
    }

    /**
     * Full pre-filled URL. If the encoded URL would exceed
     * {@link #MAX_URL_LENGTH}, the user comment is trimmed first and then the
     * finding message, each with a {@code …[truncated]} marker, until it fits.
     */
    public static String issueUrl(String repoSlug, FalsePositiveReport report) {
        if (!isValidRepoSlug(repoSlug)) {
            throw new IllegalArgumentException("Invalid GitHub repo slug: " + repoSlug);
        }
        String base = "https://github.com/" + repoSlug.trim()
                + "/issues/new?labels=false-positive";
        FalsePositiveReport r = report;
        String url = compose(base, r);
        // Iteratively shrink the two free-text fields until the URL fits.
        while (url.length() > MAX_URL_LENGTH) {
            String comment = r.userComment() == null ? "" : r.userComment();
            String message = r.message() == null ? "" : r.message();
            int over = url.length() - MAX_URL_LENGTH;
            if (comment.length() > TRUNCATION_MARKER.length()) {
                comment = trim(comment, over);
            } else if (message.length() > TRUNCATION_MARKER.length()) {
                message = trim(message, over);
            } else {
                break; // nothing left to trim — return the over-long URL as last resort
            }
            r = r.withCommentAndMessage(comment, message);
            url = compose(base, r);
        }
        return url;
    }

    private static String compose(String base, FalsePositiveReport r) {
        return base
                + "&title=" + enc(issueTitle(r))
                + "&body=" + enc(issueBody(r));
    }

    /** Drop {@code chars} (plus the marker) off the raw end of {@code s}. */
    private static String trim(String s, int chars) {
        int keep = Math.max(0, s.length() - Math.max(chars, 1) - TRUNCATION_MARKER.length());
        return s.substring(0, keep) + TRUNCATION_MARKER;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void row(StringBuilder b, String label, String value) {
        b.append("| ").append(label).append(" | ")
                .append(blankToDash(value).replace("|", "\\|").replace("\n", " "))
                .append(" |\n");
    }

    private static String join(String a, String b) {
        boolean ha = notBlank(a), hb = notBlank(b);
        if (ha && hb) return a.trim() + " — " + b.trim();
        if (ha) return a.trim();
        if (hb) return b.trim();
        return "";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToDash(String s) {
        return notBlank(s) ? s.trim() : "—";
    }
}
