package com.findatex.validator.web.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reduces a browser User-Agent to the two coarse classes stored with a web
 * usage event: device ({@code desktop | mobile | bot | unknown}) and OS family
 * ({@code Windows | Mac | Linux | iOS | Android | Other}). Deliberately a
 * handful of substring checks — the goal is a rough split for the dashboard,
 * not fingerprinting. Never returns any other part of the UA.
 *
 * <p>Why this exists: a web run used to record the <em>server's</em>
 * {@code os.name}, so every browser run showed up as "Linux".
 */
public final class UserAgentClassifier {

    /** Same cap as the viewer apps' {@code user_agent} column. */
    static final int MAX_UA = 255;

    private static final Pattern MOBILE = Pattern.compile(
            "Mobi|Android|iPhone|iPad|iPod|Windows Phone|IEMobile|BlackBerry|Opera Mini",
            Pattern.CASE_INSENSITIVE);

    private UserAgentClassifier() {
    }

    public static String device(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "unknown";
        if (BotDetector.isBot(userAgent)) return "bot";
        if (MOBILE.matcher(userAgent).find()) return "mobile";
        return "desktop";
    }

    /** OS family; {@code null} for a missing UA (the column stays NULL). */
    public static String osFamily(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return null;
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) return "iOS";
        if (ua.contains("android")) return "Android";
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("mac os") || ua.contains("macintosh") || ua.contains("darwin")) return "Mac";
        if (ua.contains("cros ")) return "Linux";
        if (ua.contains("linux") || ua.contains("x11") || ua.contains("freebsd")) return "Linux";
        return "Other";
    }

    /** Trimmed, capped copy for storage; {@code null} when blank. */
    public static String truncate(String userAgent) {
        if (userAgent == null) return null;
        String t = userAgent.trim();
        if (t.isEmpty()) return null;
        return t.length() > MAX_UA ? t.substring(0, MAX_UA) : t;
    }
}
