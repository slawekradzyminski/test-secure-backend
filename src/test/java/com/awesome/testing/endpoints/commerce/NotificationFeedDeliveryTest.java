package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.giftcard.GiftCardCreateDto;
import com.awesome.testing.dto.giftcard.GiftCardDto;
import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.password.ForgotPasswordRequestDto;
import com.awesome.testing.dto.payment.PaymentResultResponseDto;
import com.awesome.testing.dto.webhook.WebhookCreateDto;
import com.awesome.testing.dto.webhook.WebhookInboxDto;
import com.awesome.testing.dto.webhook.WebhookSubscriptionDto;
import com.awesome.testing.dto.wishlist.WishlistUpdateDto;
import com.awesome.testing.dto.wishlist.WishlistItemDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.EmailEventEntity;
import com.awesome.testing.entity.webhook.WebhookDeliveryEntity;
import com.awesome.testing.internal.dto.InternalNotificationDto;
import com.awesome.testing.repository.EmailEventRepository;
import com.awesome.testing.repository.event.DomainEventRepository;
import com.awesome.testing.repository.webhook.WebhookDeliveryRepository;
import com.awesome.testing.service.notification.CustomerNotificationRequestedEvent;
import com.awesome.testing.service.webhook.WebhookEventTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code notifications.delivery=feed}: every commerce email becomes an internal
 * {@code notification.requested} feed event for the notification-service, written in the transaction of
 * the change that caused it; account emails still go out from the backend.
 */
@TestPropertySource(properties = "notifications.delivery=feed")
class NotificationFeedDeliveryTest extends AbstractTenderFlowTest {

    private static final String NOTIFICATION_TOKEN = "test-notification-token";
    private static final String TYPE = "notification.requested";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private DomainEventRepository domainEvents;

    @Autowired
    private EmailEventRepository emailEventRepository;

    @Autowired
    private WebhookDeliveryRepository webhookDeliveries;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aCapturedPaymentRecordsPaymentReceivedAndLoyaltyEventsInsteadOfEmails() {
        // given
        long before = domainEvents.maxSequence();
        OrderDto order = order(clientToken, product("12.50", 5), 2);
        UUID resultRequestId = UUID.randomUUID();

        // when
        ResponseEntity<PaymentResultResponseDto> paid = postResult(order.getId(),
                captured(resultRequestId, UUID.randomUUID(), "25.00"), PaymentResultResponseDto.class);
        ResponseEntity<String> filtered = feed(before, TYPE);
        ResponseEntity<String> unfiltered = feed(before, null);

        // then
        assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<JsonNode> notifications = events(filtered);
        assertThat(notifications).allSatisfy(event -> assertThat(event.get("type").asString()).isEqualTo(TYPE));
        JsonNode received = forRecipient(notifications, EmailTemplate.PAYMENT_RECEIVED);
        assertThat(fieldNames(received)).containsExactly("sequence", "id", "type", "occurredAt", "orderId", "username",
                "data", "requestId", "traceId");
        assertThat(received.get("orderId").isNull()).isTrue();
        assertThat(received.get("username").asString()).isEqualTo(client.getUsername());
        UUID expectedRequestId = UUID.nameUUIDFromBytes(
                ("payment-result:" + resultRequestId).getBytes(StandardCharsets.UTF_8));
        assertThat(received.get("data").toString()).isEqualTo("{\"properties\":{\"amount\":\"25.00\",\"currency\":\"USD\","
                + "\"orderId\":\"" + order.getId() + "\"},\"recipientEmail\":\"" + client.getEmail()
                + "\",\"recipientName\":\"" + client.getFirstName() + " " + client.getLastName()
                + "\",\"requestId\":\"" + expectedRequestId + "\",\"template\":\"PAYMENT_RECEIVED\"}");
        assertThat(forRecipient(notifications, EmailTemplate.LOYALTY_POINTS_EARNED).get("data").get("properties")
                .toString()).isEqualTo("{\"balance\":\"25\",\"orderId\":\"" + order.getId() + "\",\"points\":\"25\"}");
        assertThat(events(unfiltered)).extracting(event -> event.get("type").asString())
                .contains("order.created", "order.paid", TYPE);
        assertThat(emailsTo(client.getEmail())).isEmpty();
    }

    @Test
    void aFailedPaymentRecordsPaymentFailed() {
        // given
        long before = domainEvents.maxSequence();
        OrderDto order = order(clientToken, product("7.00", 5), 1);

        // when
        postResult(order.getId(), failed(UUID.randomUUID(), UUID.randomUUID(), "7.00", "card_declined"),
                PaymentResultResponseDto.class);

        // then
        assertThat(forRecipient(events(feed(before, TYPE)), EmailTemplate.PAYMENT_FAILED).get("data").get("properties")
                .toString()).isEqualTo("{\"failureCode\":\"card_declined\",\"orderId\":\"" + order.getId() + "\"}");
        assertThat(emailsTo(client.getEmail())).isEmpty();
    }

    @Test
    void warehouseNotificationsKeepTheirRequestIdAreRecordedOnceAndStillValidated() {
        // given
        long before = domainEvents.maxSequence();
        UUID requestId = UUID.randomUUID();
        InternalNotificationDto shipped = new InternalNotificationDto(requestId, client.getUsername(),
                EmailTemplate.ORDER_SHIPPED, Map.of("orderId", "1", "carrier", "DPD", "trackingNumber", "123"));

        // when
        ResponseEntity<Void> accepted = notification(shipped, Void.class);
        ResponseEntity<Void> replayed = notification(shipped, Void.class);
        ResponseEntity<ErrorDto> missingProperty = notification(new InternalNotificationDto(UUID.randomUUID(),
                client.getUsername(), EmailTemplate.RETURN_UPDATED, Map.of("orderId", "1")), ErrorDto.class);
        ResponseEntity<ErrorDto> unknownUser = notification(new InternalNotificationDto(UUID.randomUUID(),
                "nobody-" + UUID.randomUUID(), EmailTemplate.ORDER_DELIVERED, Map.of("orderId", "1")), ErrorDto.class);
        ResponseEntity<ErrorDto> accountTemplate = notification(new InternalNotificationDto(UUID.randomUUID(),
                client.getUsername(), EmailTemplate.PASSWORD_RESET_REQUESTED, Map.of()), ErrorDto.class);

        // then
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(missingProperty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingProperty.getBody().getMessage()).isEqualTo("properties.returnId is required for RETURN_UPDATED");
        assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(accountTemplate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<JsonNode> notifications = forUser(events(feed(before, TYPE)), client.getUsername());
        assertThat(notifications).singleElement().satisfies(event -> {
            assertThat(event.get("data").get("requestId").asString()).isEqualTo(requestId.toString());
            assertThat(event.get("data").get("template").asString()).isEqualTo("ORDER_SHIPPED");
            assertThat(event.get("data").get("properties").toString())
                    .isEqualTo("{\"carrier\":\"DPD\",\"orderId\":\"1\",\"trackingNumber\":\"123\"}");
        });
        assertThat(emailsTo(client.getEmail())).isEmpty();
    }

    @Test
    void issuingAGiftCardToACustomerRecordsGiftCardIssued() {
        // given
        long before = domainEvents.maxSequence();

        // when
        GiftCardDto card = issueGiftCard(GiftCardCreateDto.builder().amount(new BigDecimal("40"))
                .recipientUsername(client.getUsername()).build());

        // then
        assertThat(forRecipient(events(feed(before, TYPE)), EmailTemplate.GIFT_CARD_ISSUED).get("data").get("properties")
                .toString()).isEqualTo("{\"amount\":\"40.00\",\"code\":\"" + card.getCode() + "\"}");
        assertThat(emailsTo(client.getEmail())).isEmpty();
    }

    @Test
    void restockingRecordsBackInStockForWatchersWithTheEventIdAsRequestId() {
        // given
        long before = domainEvents.maxSequence();
        ProductEntity product = product("10.00", 0);
        executePut("/api/v1/wishlist/" + product.getId(), new WishlistUpdateDto(true), getHeadersWith(clientToken),
                WishlistItemDto.class);

        // when
        ResponseEntity<String> adjusted = executePost("/api/v1/admin/inventory/" + product.getId() + "/adjustments",
                InventoryAdjustmentDto.builder().delta(3).reason("restock").requestId(UUID.randomUUID()).build(),
                getHeadersWith(adminToken), String.class);

        // then
        assertThat(adjusted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode event = forRecipient(events(feed(before, TYPE)), EmailTemplate.BACK_IN_STOCK);
        assertThat(event.get("data").get("requestId").asString()).isEqualTo(event.get("id").asString());
        assertThat(event.get("data").get("properties").toString())
                .isEqualTo("{\"productId\":\"" + product.getId() + "\",\"productName\":\"" + product.getName() + "\"}");
        assertThat(emailsTo(client.getEmail())).isEmpty();
    }

    @Test
    void theEventExistsOnlyWhenTheBusinessTransactionCommits() {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID rolledBack = UUID.randomUUID();
        UUID committed = UUID.randomUUID();

        // when
        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(request(rolledBack));
            status.setRollbackOnly();
        });
        transaction.executeWithoutResult(status -> eventPublisher.publishEvent(request(committed)));

        // then
        assertThat(domainEvents.findByEventId(eventIdFor(rolledBack))).isEmpty();
        assertThat(domainEvents.findByEventId(eventIdFor(committed))).isPresent();
        assertThat(emailsTo(client.getEmail())).isEmpty();
    }

    @Test
    void accountEmailsStillLeaveTheBackend() {
        // when
        ResponseEntity<String> forgot = executePost("/api/v1/users/password/forgot",
                ForgotPasswordRequestDto.builder().identifier(client.getUsername()).build(), getJsonOnlyHeaders(),
                String.class);

        // then
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(emailsTo(client.getEmail())).extracting(EmailEventEntity::getType)
                .containsExactly(EmailTemplate.PASSWORD_RESET_REQUESTED);
    }

    @Test
    void notificationRequestedIsNeverOfferedToUserWebhooks() {
        // given
        WebhookInboxDto inbox = executePost("/api/v1/webhook-inboxes", null, getHeadersWith(clientToken),
                WebhookInboxDto.class).getBody();
        List<String> everyType = new ArrayList<>(WebhookEventTypes.names());
        WebhookSubscriptionDto hook = executePost("/api/v1/webhooks", WebhookCreateDto.builder().url(inbox.url())
                .events(everyType).build(), getHeadersWith(clientToken), WebhookSubscriptionDto.class).getBody();
        OrderDto order = order(clientToken, product("5.00", 5), 1);

        // when
        ResponseEntity<ErrorDto> named = executePost("/api/v1/webhooks", WebhookCreateDto.builder().url(inbox.url())
                .events(List.of(TYPE)).build(), getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<String> eventTypes = executeGet("/api/v1/webhooks/event-types", getHeadersWith(clientToken),
                String.class);
        postResult(order.getId(), captured(UUID.randomUUID(), UUID.randomUUID(), "5.00"), PaymentResultResponseDto.class);

        // then
        assertThat(everyType).doesNotContain(TYPE);
        assertThat(named.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(named.getBody().getMessage()).startsWith("Unknown event type 'notification.requested'");
        assertThat(eventTypes.getBody()).doesNotContain(TYPE);
        assertThat(webhookDeliveries.findBySubscriptionId(hook.getId(), Pageable.unpaged()).getContent())
                .extracting(WebhookDeliveryEntity::getEventType)
                .contains("order.created", "order.paid")
                .doesNotContain(TYPE);
    }

    private CustomerNotificationRequestedEvent request(UUID requestId) {
        return new CustomerNotificationRequestedEvent(requestId, client.getUsername(), EmailTemplate.ORDER_DELIVERED,
                Map.of("orderId", "9"));
    }

    private static UUID eventIdFor(UUID requestId) {
        return UUID.nameUUIDFromBytes((TYPE + ":" + requestId).getBytes(StandardCharsets.UTF_8));
    }

    private <T> ResponseEntity<T> notification(InternalNotificationDto notification, Class<T> type) {
        return executePost("/internal/v1/notifications", notification, serviceHeaders(WAREHOUSE_TOKEN), type);
    }

    private ResponseEntity<String> feed(long after, String types) {
        String query = "?limit=200&after=" + after + (types == null ? "" : "&types=" + types);
        return executeGet("/internal/v1/events" + query, serviceHeaders(NOTIFICATION_TOKEN), String.class);
    }

    private List<EmailEventEntity> emailsTo(String email) {
        return emailEventRepository.findAll().stream().filter(event -> email.equals(event.getRecipientEmail())).toList();
    }

    private static List<JsonNode> events(ResponseEntity<String> response) {
        List<JsonNode> events = new ArrayList<>();
        JSON.readTree(response.getBody()).get("events").forEach(events::add);
        return events;
    }

    private static List<JsonNode> forUser(List<JsonNode> events, String username) {
        return events.stream().filter(event -> username.equals(event.get("username").asString())).toList();
    }

    private JsonNode forRecipient(List<JsonNode> events, EmailTemplate template) {
        List<JsonNode> matching = forUser(events, client.getUsername()).stream()
                .filter(event -> template.name().equals(event.get("data").get("template").asString()))
                .toList();
        assertThat(matching).as(template + " events for " + client.getUsername()).hasSize(1);
        return matching.getFirst();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
