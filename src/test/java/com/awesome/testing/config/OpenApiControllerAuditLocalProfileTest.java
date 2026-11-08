package com.awesome.testing.config;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test", "local"})
@LocalProfileOnH2
class OpenApiControllerAuditLocalProfileTest extends OpenApiControllerAuditTest {
}
