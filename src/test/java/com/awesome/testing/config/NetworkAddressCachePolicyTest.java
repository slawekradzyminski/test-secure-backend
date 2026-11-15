package com.awesome.testing.config;

import org.junit.jupiter.api.Test;

import java.security.Security;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkAddressCachePolicyTest {

    @Test
    void failedLookupsAreNotCached() {
        String previous = Security.getProperty(NetworkAddressCachePolicy.NEGATIVE_TTL);
        try {
            NetworkAddressCachePolicy.disableNegativeCaching();

            assertThat(Security.getProperty(NetworkAddressCachePolicy.NEGATIVE_TTL)).isEqualTo("0");
        } finally {
            Security.setProperty(NetworkAddressCachePolicy.NEGATIVE_TTL, previous);
        }
    }
}
