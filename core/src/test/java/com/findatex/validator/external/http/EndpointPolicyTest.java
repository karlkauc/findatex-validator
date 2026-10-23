package com.findatex.validator.external.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointPolicyTest {

    @Test
    void httpsAndLoopbackHttpAreAllowed() {
        assertThat(EndpointPolicy.isAllowed("https://www.findatex-validator.eu")).isTrue();
        assertThat(EndpointPolicy.isAllowed("https://www.findatex-validator.eu/api/usage-stats")).isTrue();
        assertThat(EndpointPolicy.isAllowed("http://127.0.0.1:8080")).isTrue();
        assertThat(EndpointPolicy.isAllowed("http://localhost:8080/api/usage-stats")).isTrue();
    }

    @Test
    void cleartextAndOddUrlsAreRefused() {
        assertThat(EndpointPolicy.isAllowed("http://www.findatex-validator.eu")).isFalse();
        assertThat(EndpointPolicy.isAllowed("http://10.0.0.1:8080")).isFalse();
        assertThat(EndpointPolicy.isAllowed("ftp://example.org")).isFalse();
        assertThat(EndpointPolicy.isAllowed("file:///etc/passwd")).isFalse();
        assertThat(EndpointPolicy.isAllowed("https://user:pw@example.org")).isFalse();
        assertThat(EndpointPolicy.isAllowed("not a url")).isFalse();
        assertThat(EndpointPolicy.isAllowed("")).isFalse();
        assertThat(EndpointPolicy.isAllowed(null)).isFalse();
    }
}
