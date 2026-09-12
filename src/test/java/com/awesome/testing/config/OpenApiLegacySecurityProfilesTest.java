package com.awesome.testing.config;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.traffic.legacy-public-access=true",
        "password-reset.require-outbox-access-key=false"
})
class OpenApiLegacySecurityProfilesTest extends OpenApiSecurityProfilesTest {
}
