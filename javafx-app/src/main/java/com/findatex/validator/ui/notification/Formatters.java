package com.findatex.validator.ui.notification;

import java.time.Duration;
import java.util.Locale;

public final class Formatters {

    private Formatters() {}

    public static String humanBytes(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.ROOT, "%.1f GB", gb);
    }

    public static String humanDuration(Duration d) {
        if (d == null || d.isNegative() || d.isZero()) return "0 ms";
        long ms = d.toMillis();
        if (ms < 1000) return ms + " ms";
        long totalSec = d.toSeconds();
        if (totalSec < 60) return String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return min + " min " + sec + " s";
    }
}
