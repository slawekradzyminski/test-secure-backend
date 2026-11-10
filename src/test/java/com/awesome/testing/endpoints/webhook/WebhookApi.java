package com.awesome.testing.endpoints.webhook;

import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.webhook.WebhookCreateDto;
import com.awesome.testing.dto.webhook.WebhookDeliveryDto;
import com.awesome.testing.dto.webhook.WebhookInboxDto;
import com.awesome.testing.dto.webhook.WebhookInboxRequestDto;
import com.awesome.testing.dto.webhook.WebhookSubscriptionDto;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;

/** Paths and response types shared by the webhook endpoint tests. */
final class WebhookApi {

    static final String WEBHOOKS = "/api/v1/webhooks";
    static final String ADMIN_WEBHOOKS = "/api/v1/admin/webhooks";
    static final String INBOXES = "/api/v1/webhook-inboxes";

    static final ParameterizedTypeReference<List<WebhookSubscriptionDto>> SUBSCRIPTIONS = new ParameterizedTypeReference<>() {
    };
    static final ParameterizedTypeReference<PageDto<WebhookSubscriptionDto>> SUBSCRIPTION_PAGE =
            new ParameterizedTypeReference<>() {
            };
    static final ParameterizedTypeReference<PageDto<WebhookDeliveryDto>> DELIVERY_PAGE = new ParameterizedTypeReference<>() {
    };
    static final ParameterizedTypeReference<List<WebhookInboxDto>> INBOX_LIST = new ParameterizedTypeReference<>() {
    };
    static final ParameterizedTypeReference<List<WebhookInboxRequestDto>> INBOX_REQUESTS =
            new ParameterizedTypeReference<>() {
            };

    private WebhookApi() {
    }

    static WebhookCreateDto subscription(String url, String... events) {
        return WebhookCreateDto.builder().url(url).events(List.of(events)).description("test hook").build();
    }

    static String subscriptionPath(Long id) {
        return WEBHOOKS + "/" + id;
    }
}
