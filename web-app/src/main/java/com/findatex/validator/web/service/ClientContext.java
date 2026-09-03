package com.findatex.validator.web.service;

/**
 * Everything a usage row may know about the requesting client — already
 * reduced to storable form. No raw IP: it is consumed by {@link ClientContextFactory}
 * to derive {@code countryCode} and {@code visitorHash} and then dropped.
 *
 * @param countryCode ISO country from GeoIP, or null
 * @param visitorHash daily-rotating hash (see {@link VisitorHasher}), or null
 * @param userAgent   trimmed, capped UA string, or null
 * @param device      {@code desktop | mobile | bot | unknown}
 * @param osName      OS family from the UA, or null
 */
public record ClientContext(String countryCode,
                            String visitorHash,
                            String userAgent,
                            String device,
                            String osName) {

    public static final ClientContext EMPTY = new ClientContext(null, null, null, "unknown", null);

    public boolean isBot() {
        return "bot".equals(device);
    }
}
