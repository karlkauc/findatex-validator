package com.findatex.validator.report;

/**
 * Turns a score in [0, 1] into the whole-percent number a UI shows.
 *
 * <p>Both UIs print a rounded percentage somewhere, and the two ends of the
 * scale carry a meaning the number alone does not: a reader takes 100 as
 * "nothing wrong with this file" and 0 as "nothing was right". Plain rounding
 * breaks the first — a file scoring 0.9960 (real errors, merely diluted across
 * many rows) rounds up and is presented as flawless. That is how a 60-row
 * sample with a corrupt ISIN, a corrupt LEI and eight other errors came out as
 * "100 / 100".
 *
 * <p>So: ordinary rounding in the middle, but only an exactly perfect score may
 * report 100, and only an exactly zero score may report 0. The unrounded value
 * stays available wherever precision matters — the Excel report prints one
 * decimal rather than going through here.
 */
public final class ScorePercent {

    private ScorePercent() {
    }

    /** @param score in [0, 1]; values outside are clamped. */
    public static int of(double score) {
        if (Double.isNaN(score)) return 0;
        double clamped = Math.max(0.0, Math.min(1.0, score));
        int pct = (int) Math.round(clamped * 100.0);
        if (pct >= 100 && clamped < 1.0) return 99;
        if (pct <= 0 && clamped > 0.0) return 1;
        return pct;
    }

    /** The same number with a percent sign, for direct display. */
    public static String format(double score) {
        return of(score) + "%";
    }
}
