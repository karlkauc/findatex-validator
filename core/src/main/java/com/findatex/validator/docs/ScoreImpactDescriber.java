package com.findatex.validator.docs;

import com.findatex.validator.validation.Severity;

/**
 * Translates a rule into a one-line "score impact" phrase. Mirrors the rule-prefix routing
 * used by {@link com.findatex.validator.report.QualityScorer} so the generated documentation
 * cannot drift from how findings actually affect the score.
 *
 * <p>Centralised here (instead of inlined in the generator) so a future weight change in
 * {@code QualityScorer} only needs one corresponding edit in this class.
 */
final class ScoreImpactDescriber {

    private ScoreImpactDescriber() {
    }

    /** "−1 in MANDATORY_COMPLETENESS (40 %), −1 in PROFILE_COMPLETENESS (10 %)". */
    static String forPresence() {
        return "Each missing cell lowers MANDATORY_COMPLETENESS (40 %) by 1 / total mandatory slots,"
                + " and lowers the per-profile PROFILE_COMPLETENESS leg (10 %, M-weighted 0.7).";
    }

    /** Conditional presence (C-flagged) finding (severity WARNING). */
    static String forConditionalPresence() {
        return "Each missing cell lowers PROFILE_COMPLETENESS (10 %, C-weighted 0.3) by"
                + " 1 / total conditional slots. Severity = WARNING — does not affect"
                + " MANDATORY_COMPLETENESS or FORMAT_CONFORMANCE.";
    }

    /** FORMAT/* findings — heuristically routed to FORMAT or CLOSED_LIST in QualityScorer. */
    static String forFormat() {
        return "Each ERROR lowers FORMAT_CONFORMANCE (20 %) by 1 / non-empty cells. Closed-list"
                + " mismatches (message contains \"closed list\") instead lower CLOSED_LIST_CONFORMANCE"
                + " (15 %) by 1 / populated closed-list cells.";
    }

    /** ISIN/* and LEI/* findings — both route to FORMAT_CONFORMANCE. */
    static String forIdentifier() {
        return "Each ERROR lowers FORMAT_CONFORMANCE (20 %) by 1 / non-empty cells.";
    }

    /** XF-* and template-prefixed cross-field rules. */
    static String forCrossField(Severity severity) {
        if (severity == Severity.INFO) {
            return "Severity = INFO — surfaced in the report but not factored into the score.";
        }
        if (severity == Severity.WARNING) {
            return "Severity = WARNING — surfaced in the report but not factored into the score"
                    + " (only ERROR severity feeds CROSS_FIELD_CONSISTENCY).";
        }
        return "Each ERROR lowers CROSS_FIELD_CONSISTENCY (15 %) by"
                + " 1 / max(distinct cross-field rules × rows, 1).";
    }

    /** External validation findings (GLEIF / OpenFIGI) are advisory and do not affect scoring. */
    static String forExternal() {
        return "External-validation findings are advisory and do not affect any score dimension.";
    }
}
