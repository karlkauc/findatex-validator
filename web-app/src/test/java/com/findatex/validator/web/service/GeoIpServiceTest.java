package com.findatex.validator.web.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With no GeoLite2 database configured (default test profile) the service must
 * stay up and return {@code null} — never fail the boot or a request.
 */
@QuarkusTest
class GeoIpServiceTest {

    @Inject
    GeoIpService geoIp;

    @Test
    void noDatabaseConfiguredReturnsNull() {
        assertThat(geoIp.countryFor("8.8.8.8")).isNull();
        assertThat(geoIp.countryFor(null)).isNull();
        assertThat(geoIp.countryFor("")).isNull();
        assertThat(geoIp.countryFor("not-an-ip")).isNull();
    }
}
