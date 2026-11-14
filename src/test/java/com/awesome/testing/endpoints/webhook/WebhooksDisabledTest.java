package com.awesome.testing.endpoints.webhook;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.webhook.WebhookDeliveryStatus;
import com.awesome.testing.dto.webhook.WebhookInboxDto;
import com.awesome.testing.dto.webhook.WebhookInboxRequestDto;
import com.awesome.testing.dto.webhook.WebhookSubscriptionDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.webhook.WebhookDeliveryEntity;
import com.awesome.testing.repository.webhook.WebhookDeliveryRepository;
import com.awesome.testing.service.webhook.WebhookRetryJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.awesome.testing.endpoints.webhook.WebhookApi.INBOXES;
import static com.awesome.testing.endpoints.webhook.WebhookApi.INBOX_REQUESTS;
import static com.awesome.testing.endpoints.webhook.WebhookApi.SUBSCRIPTIONS;
import static com.awesome.testing.endpoints.webhook.WebhookApi.WEBHOOKS;
import static com.awesome.testing.endpoints.webhook.WebhookApi.subscription;
import static com.awesome.testing.endpoints.webhook.WebhookApi.subscriptionPath;
import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code webhooks.enabled=false} (the notification-service owns webhooks): the endpoints stay mapped,
 * but the backend records no delivery, sends nothing and its retry job does no work. The export keeps
 * working for the one-time import.
 */
@TestPropertySource(properties = "webhooks.enabled=false")
class WebhooksDisabledTest extends AbstractEcommerceTest {

    @Autowired
    private WebhookDeliveryRepository deliveries;

    @Autowired
    private WebhookRetryJob retryJob;

    private String clientToken;

    @BeforeEach
    void createClient() {
        clientToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
    }

    @Test
    void endpointsStayMappedButCommerceEventsRecordAndSendNoDeliveries() {
        // given
        WebhookInboxDto inbox = executePost(INBOXES, null, getHeadersWith(clientToken), WebhookInboxDto.class).getBody();
        ResponseEntity<WebhookSubscriptionDto> created = executePost(WEBHOOKS, subscription(inbox.url(), "order.created"),
                getHeadersWith(clientToken), WebhookSubscriptionDto.class);
        ProductEntity product = productRepository.save(ProductEntity.builder().name("Disabled hooks " + System.nanoTime())
                .description("Product used by disabled webhook tests").price(new BigDecimal("3.00")).stockQuantity(5)
                .category("Tests").build());

        // when
        executePost(CART_ITEMS_ENDPOINT, CartItemDto.builder().productId(product.getId()).quantity(1).build(),
                getHeadersWith(clientToken), String.class);
        ResponseEntity<OrderDto> order = executePost(ORDERS_ENDPOINT, getRandomAddress(), getHeadersWith(clientToken),
                OrderDto.class);
        ResponseEntity<List<WebhookSubscriptionDto>> list = executeGet(WEBHOOKS, getHeadersWith(clientToken), SUBSCRIPTIONS);
        ResponseEntity<List<WebhookInboxRequestDto>> received = executeGet(INBOXES + "/" + inbox.token() + "/requests",
                getHeadersWith(clientToken), INBOX_REQUESTS);

        // then
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(order.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).extracting(WebhookSubscriptionDto::getId).containsExactly(created.getBody().getId());
        assertThat(deliveries.findBySubscriptionId(created.getBody().getId(), Pageable.unpaged())).isEmpty();
        assertThat(received.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(received.getBody()).isEmpty();
    }

    @Test
    void testPingsAndRedeliveriesAreRefusedWithoutRecordingADelivery() {
        // given
        WebhookInboxDto inbox = executePost(INBOXES, null, getHeadersWith(clientToken), WebhookInboxDto.class).getBody();
        WebhookSubscriptionDto hook = executePost(WEBHOOKS, subscription(inbox.url(), "order.created"),
                getHeadersWith(clientToken), WebhookSubscriptionDto.class).getBody();
        WebhookDeliveryEntity earlier = deliveries.save(WebhookDeliveryEntity.builder().id(UUID.randomUUID())
                .subscriptionId(hook.getId()).eventId(UUID.randomUUID()).eventType("order.created").payload("{}")
                .status(WebhookDeliveryStatus.FAILED).attempts(1).createdAt(Instant.now()).build());

        // when
        ResponseEntity<ErrorDto> ping = executePost(subscriptionPath(hook.getId()) + "/test", null,
                getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<ErrorDto> redeliver = executePost(subscriptionPath(hook.getId()) + "/deliveries/"
                + earlier.getId() + "/redeliver", null, getHeadersWith(clientToken), ErrorDto.class);

        // then
        assertThat(ping.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ping.getBody().getMessage()).isEqualTo("Webhook delivery is disabled on this backend");
        assertThat(redeliver.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(redeliver.getBody().getMessage()).isEqualTo("Webhook delivery is disabled on this backend");
        assertThat(deliveries.findBySubscriptionId(hook.getId(), Pageable.unpaged()))
                .extracting(WebhookDeliveryEntity::getId).containsExactly(earlier.getId());
    }

    @Test
    void theScheduledRetryLeavesDueDeliveriesAlone() {
        // given
        WebhookInboxDto inbox = executePost(INBOXES, null, getHeadersWith(clientToken), WebhookInboxDto.class).getBody();
        WebhookSubscriptionDto hook = executePost(WEBHOOKS, subscription(inbox.url(), "order.created"),
                getHeadersWith(clientToken), WebhookSubscriptionDto.class).getBody();
        WebhookDeliveryEntity due = deliveries.save(WebhookDeliveryEntity.builder().id(UUID.randomUUID())
                .subscriptionId(hook.getId()).eventId(UUID.randomUUID()).eventType("order.created").payload("{}")
                .status(WebhookDeliveryStatus.PENDING).attempts(0).createdAt(Instant.now())
                .nextAttemptAt(Instant.now().minusSeconds(60)).build());

        // when
        retryJob.scheduledRetry();

        // then
        WebhookDeliveryEntity after = deliveries.findById(due.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(after.getAttempts()).isZero();
    }

    @Test
    void theExportStillServesTheNotificationService() {
        // given
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Service-Token", "test-notification-token");

        // when
        ResponseEntity<String> export = executeGet("/internal/v1/webhooks/export", headers, String.class);

        // then
        assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
