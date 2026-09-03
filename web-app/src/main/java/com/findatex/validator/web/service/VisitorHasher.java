package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Daily-rotating, non-reversible visitor id for web usage events — the same
 * scheme the xsd-viewer / xml-viewer apps use, so the dashboard can treat all
 * three alike:
 *
 * <pre>visitor_hash = sha256(salt | ip | user-agent)[0..32)
 * salt           = HMAC-SHA256(secret, yyyy-MM-dd)</pre>
 *
 * <p>The same visitor hashes identically within one UTC day and differently
 * the next, so the value can count "distinct visitors today" but cannot link
 * days, and the raw IP never leaves this method. The secret matters: without
 * it a brute-force over the IPv4 space would be feasible. It also has to be
 * <em>shared</em> across Cloud Run instances — a random per-process salt
 * would split one visitor into N hashes and inflate the count. So: secret from
 * {@code FINDATEX_WEB_VISITOR_SALT_SECRET}; when unset, a per-process random
 * salt is used and a single WARN says visitor counts are approximate.
 */
@ApplicationScoped
public class VisitorHasher {

    private static final Logger log = LoggerFactory.getLogger(VisitorHasher.class);
    private static final HexFormat HEX = HexFormat.of();

    private final Optional<String> secret;
    private final String processSalt;
    private final AtomicReference<DaySalt> current = new AtomicReference<>();
    private volatile boolean warned;

    @Inject
    public VisitorHasher(WebConfig config) {
        this(config.usageStats().visitorSaltSecret());
    }

    /** Test seam: explicit secret (empty = per-process random salt). */
    VisitorHasher(Optional<String> secret) {
        this.secret = secret == null ? Optional.empty() : secret.filter(s -> !s.isBlank());
        byte[] rnd = new byte[32];
        new SecureRandom().nextBytes(rnd);
        this.processSalt = HEX.formatHex(rnd);
    }

    /** Hash for "now" (UTC day). {@code null} when there is no IP to hash. */
    public String hash(String ip, String userAgent) {
        return hash(ip, userAgent, LocalDate.now(ZoneOffset.UTC));
    }

    String hash(String ip, String userAgent, LocalDate day) {
        if (ip == null || ip.isBlank()) return null;
        String salt = saltFor(day);
        String material = salt + "|" + ip.trim() + "|" + (userAgent == null ? "" : userAgent);
        return HEX.formatHex(sha256(material)).substring(0, 32);
    }

    private String saltFor(LocalDate day) {
        DaySalt ds = current.get();
        if (ds != null && ds.day.equals(day)) return ds.salt;
        String salt = computeSalt(day);
        current.set(new DaySalt(day, salt));
        return salt;
    }

    private String computeSalt(LocalDate day) {
        String dayKey = day.toString();
        if (secret.isPresent()) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.get().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return HEX.formatHex(mac.doFinal(dayKey.getBytes(StandardCharsets.UTF_8)));
            } catch (java.security.GeneralSecurityException e) {
                log.warn("Visitor-hash HMAC unavailable ({}); using per-process salt", e.toString());
            }
        } else if (!warned) {
            warned = true;
            log.warn("FINDATEX_WEB_VISITOR_SALT_SECRET is not set: visitor hashes use a per-process "
                    + "salt, so visitor counts across instances/restarts are approximate");
        }
        return processSalt + "|" + dayKey;
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record DaySalt(LocalDate day, String salt) {
    }
}
