package com.awesome.testing.internal.controller;

import com.awesome.testing.dto.webhook.WebhookExportDto;
import com.awesome.testing.internal.InternalApi;
import com.awesome.testing.service.webhook.WebhookExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One-time webhook export for the notification-service (its caller only, see
 * {@link com.awesome.testing.internal.InternalApiConfig}). It contains the signing secrets, so it
 * exists only behind the service token and is undocumented in OpenAPI like the rest of the
 * internal API.
 */
@RestController
@RequiredArgsConstructor
public class InternalWebhookExportController {

    private final WebhookExportService webhookExportService;

    @GetMapping(path = InternalApi.BASE_PATH + "/webhooks/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public WebhookExportDto export() {
        return webhookExportService.export();
    }
}
