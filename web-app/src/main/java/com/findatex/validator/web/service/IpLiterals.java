package com.findatex.validator.web.service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Strict IP-literal handling for request-derived addresses. Anything that ends
 * up as a client IP (rate-limit key, GeoIP lookup, visitor hash) may have come
 * from a forwarded header, so it must never reach {@link InetAddress#getByName}
 * as a hostname — that would trigger a DNS query for an attacker-chosen name.
 */
public final class IpLiterals {

    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    /** Hex groups, colons and an optional embedded IPv4 tail — no zone id, no brackets. */
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private IpLiterals() {
    }

    /**
     * Parses {@code value} as an IPv4/IPv6 literal. Returns empty for anything
     * else (hostnames, ports, garbage). Never resolves DNS: a string that passes
     * either pattern is a literal by construction, which {@code getByName}
     * parses without a lookup.
     */
    public static Optional<InetAddress> parse(String value) {
        if (value == null) return Optional.empty();
        String s = value.trim();
        if (s.isEmpty()) return Optional.empty();
        boolean v4 = IPV4.matcher(s).matches();
        boolean v6 = !v4 && s.indexOf(':') >= 0 && IPV6_CHARS.matcher(s).matches();
        if (!v4 && !v6) return Optional.empty();
        try {
            return Optional.of(InetAddress.getByName(s));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    /** Canonical textual form of a literal, or empty when {@code value} is not one. */
    public static Optional<String> normalise(String value) {
        return parse(value).map(InetAddress::getHostAddress);
    }

    /**
     * Rate-limit bucket key: the IPv4 address as is, IPv6 reduced to its /64
     * prefix. A single IPv6 subscriber typically gets a whole /64, so keying on
     * the full address would let one client rotate through 2^64 buckets.
     */
    public static Optional<String> rateLimitKey(String value) {
        return parse(value).map(addr -> {
            if (!(addr instanceof Inet6Address)) return addr.getHostAddress();
            byte[] b = addr.getAddress();
            StringBuilder sb = new StringBuilder(24);
            for (int i = 0; i < 8; i += 2) {
                sb.append(Integer.toHexString(((b[i] & 0xff) << 8) | (b[i + 1] & 0xff))).append(':');
            }
            return sb.append(":/64").toString();
        });
    }
}
