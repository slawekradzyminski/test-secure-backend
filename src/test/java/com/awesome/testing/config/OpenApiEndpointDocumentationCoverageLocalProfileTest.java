package com.awesome.testing.config;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test", "local"})
class OpenApiEndpointDocumentationCoverageLocalProfileTest extends OpenApiEndpointDocumentationCoverageTest {
}
