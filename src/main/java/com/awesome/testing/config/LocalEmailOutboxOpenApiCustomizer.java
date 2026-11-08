package com.awesome.testing.config;

import com.awesome.testing.service.password.LocalEmailOutboxAvailability;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/** Keeps the local email outbox out of the published contract wherever the outbox is inactive. */
@Component
@RequiredArgsConstructor
public class LocalEmailOutboxOpenApiCustomizer implements OpenApiCustomizer {

    static final String OUTBOX_PATH = "/api/v1/local/email/outbox";

    private final LocalEmailOutboxAvailability localEmailOutboxAvailability;

    @Override
    public void customise(io.swagger.v3.oas.models.OpenAPI openApi) {
        if (!localEmailOutboxAvailability.isActive() && openApi.getPaths() != null) {
            openApi.getPaths().remove(OUTBOX_PATH);
        }
    }
}
