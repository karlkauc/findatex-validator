package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves an ISO-3166-1 alpha-2 country from a request source IP using a
 * locally bundled MaxMind GeoLite2-Country database. No third-party call is
 * made and <b>the IP is never stored or logged</b> — only the derived country
 * code leaves this class. When no MMDB is configured/readable the service stays
 * up and {@link #countryFor} returns {@code null} (country_code becomes NULL).
 */
@ApplicationScoped
public class GeoIpService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);

    @Inject
    WebConfig config;

    private volatile DatabaseReader reader;

    @PostConstruct
    void init() {
        String path = config.usageStats().geoipDbPath().orElse(null);
        if (path == null) {
            log.info("GeoIP: no database configured (FINDATEX_WEB_GEOIP_DB unset) — country_code will be NULL");
            return;
        }
        Path p = Path.of(path);
        if (!Files.isReadable(p)) {
            log.warn("GeoIP: database '{}' not readable — country_code will be NULL", path);
            return;
        }
        try {
            reader = new DatabaseReader.Builder(new File(path))
                    .withCache(new CHMCache())
                    .build();
            log.info("GeoIP: country database loaded from {}", path);
        } catch (Exception e) {
            log.warn("GeoIP: failed to open database '{}' ({}) — country_code will be NULL",
                    path, e.toString());
        }
    }

    /**
     * Returns the ISO country code for {@code ip}, or {@code null} if unknown,
     * unresolvable, or no database is loaded. The IP is intentionally never
     * logged here, even on failure.
     */
    public String countryFor(String ip) {
        DatabaseReader r = reader;
        if (r == null || ip == null || ip.isBlank()) return null;
        try {
            InetAddress addr = InetAddress.getByName(ip.trim());
            CountryResponse resp = r.country(addr);
            String iso = resp.getCountry() == null ? null : resp.getCountry().getIsoCode();
            return (iso == null || iso.isBlank()) ? null : iso;
        } catch (Exception e) {
            // Private/loopback ranges and not-found entries land here; never log the IP.
            log.debug("GeoIP: lookup miss ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    @PreDestroy
    void close() {
        DatabaseReader r = reader;
        if (r != null) {
            try {
                r.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }
}
