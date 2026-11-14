package com.awesome.testing.endpoints.notification;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.webhook.WebhookCreateDto;
import com.awesome.testing.dto.webhook.WebhookInboxDto;
import com.awesome.testing.dto.webhook.WebhookSubscriptionDto;
import com.awesome.testing.entity.webhook.WebhookSubscriptionEntity;
import com.awesome.testing.repository.webhook.WebhookSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The NOTIFICATION service caller: it reads the event feed, the JWKS and the one-time webhook export
 * ({@code GET /internal/v1/webhooks/export}) and nothing else; no other caller reads the export.
 */
class NotificationServiceCallerTest extends DomainHelper {

    static final String EXPORT = "/internal/v1/webhooks/export";
    static final String NOTIFICATION_TOKEN = "test-notification-token";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private WebhookSubscriptionRepository subscriptionRepository;

    @Test
    void exportReturnsEverySubscriptionWithItsPlainSecretAndEveryInbox() {
        // given
        String token = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        WebhookInboxDto inbox = executePost("/api/v1/webhook-inboxes", null, getHeadersWith(token),
                WebhookInboxDto.class).getBody();
        WebhookSubscriptionDto created = executePost("/api/v1/webhooks", WebhookCreateDto.builder()
                        .url(inbox.url()).events(List.of("order.created", "order.paid")).description("export hook").build(),
                getHeadersWith(token), WebhookSubscriptionDto.class).getBody();
        WebhookSubscriptionEntity stored = subscriptionRepository.findById(created.getId()).orElseThrow();
        stored.setConsecutiveFailures(3);
        subscriptionRepository.save(stored);

        // when
        ResponseEntity<String> response = executeGet(EXPORT, serviceHeaders(NOTIFICATION_TOKEN), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(fieldNames(body)).containsExactly("subscriptions", "inboxes");
        JsonNode subscription = find(body.get("subscriptions"), "id", String.valueOf(created.getId()));
        assertThat(fieldNames(subscription)).containsExactly("id", "username", "url", "events", "secret", "active",
                "description", "createdAt", "updatedAt", "consecutiveFailures");
        assertThat(subscription.get("username").asString()).isEqualTo(created.getUsername());
        assertThat(subscription.get("url").asString()).isEqualTo(inbox.url());
        assertThat(subscription.get("events").toString()).isEqualTo("[\"order.created\",\"order.paid\"]");
        assertThat(subscription.get("secret").asString()).isEqualTo(created.getSecret()).startsWith("whsec_");
        assertThat(subscription.get("active").asBoolean()).isTrue();
        assertThat(subscription.get("description").asString()).isEqualTo("export hook");
        assertThat(subscription.get("createdAt").asString()).isNotBlank();
        assertThat(subscription.get("updatedAt").asString()).isNotBlank();
        assertThat(subscription.get("consecutiveFailures").asInt()).isEqualTo(3);
        JsonNode exportedInbox = find(body.get("inboxes"), "token", inbox.token());
        assertThat(fieldNames(exportedInbox)).containsExactly("token", "username", "createdAt");
        assertThat(exportedInbox.get("username").asString()).isEqualTo(created.getUsername());
        assertThat(response.getBody()).doesNotContain("deliveries").doesNotContain("requests");
    }

    @Test
    void exportRejectsAMissingOrWrongTokenAndEveryOtherCaller() {
        // when
        ResponseEntity<ErrorDto> missing = executeGet(EXPORT, getJsonOnlyHeaders(), ErrorDto.class);
        ResponseEntity<ErrorDto> wrong = executeGet(EXPORT, serviceHeaders("wrong-token"), ErrorDto.class);
        ResponseEntity<ErrorDto> warehouse = executeGet(EXPORT, serviceHeaders("test-warehouse-token"), ErrorDto.class);
        ResponseEntity<ErrorDto> payment = executeGet(EXPORT, serviceHeaders("test-payment-token"), ErrorDto.class);
        ResponseEntity<ErrorDto> adminJwt = executeGet(EXPORT,
                getHeadersWith(getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)))), ErrorDto.class);

        // then
        for (ResponseEntity<ErrorDto> response : List.of(missing, wrong, warehouse, payment, adminJwt)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().getMessage()).isEqualTo("Invalid service token");
        }
    }

    @Test
    void exportOnlyAnswersGet() {
        // when
        ResponseEntity<ErrorDto> post = executePost(EXPORT, "{}", serviceHeaders(NOTIFICATION_TOKEN), ErrorDto.class);

        // then
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void theNotificationCallerReadsTheFeedAndTheJwksButNoWarehouseOrPaymentEndpoint() {
        // when
        ResponseEntity<String> feed = executeGet("/internal/v1/events?after=0&limit=1",
                serviceHeaders(NOTIFICATION_TOKEN), String.class);
        ResponseEntity<String> jwks = executeGet("/internal/v1/jwks.json", serviceHeaders(NOTIFICATION_TOKEN), String.class);
        List<ResponseEntity<ErrorDto>> rejected = new ArrayList<>();
        rejected.add(executeGet("/internal/v1/products", serviceHeaders(NOTIFICATION_TOKEN), ErrorDto.class));
        rejected.add(executeGet("/internal/v1/orders/1", serviceHeaders(NOTIFICATION_TOKEN), ErrorDto.class));
        rejected.add(executeGet("/internal/v1/orders", serviceHeaders(NOTIFICATION_TOKEN), ErrorDto.class));
        rejected.add(executePost("/internal/v1/notifications", "{}", serviceHeaders(NOTIFICATION_TOKEN), ErrorDto.class));
        rejected.add(executePost("/internal/v1/orders/1/payment", "{}", serviceHeaders(NOTIFICATION_TOKEN), ErrorDto.class));
        rejected.add(execute(HttpMethod.GET, "/internal/v1/events/1", null, serviceHeaders(NOTIFICATION_TOKEN),
                ErrorDto.class));

        // then
        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(feed.getBody()).contains("\"events\"");
        assertThat(jwks.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jwks.getBody()).contains("\"keys\"");
        assertThat(rejected).allSatisfy(response ->
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private HttpHeaders serviceHeaders(String token) {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Service-Token", token);
        return headers;
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (node.get(field).asString().equals(value)) {
                return node;
            }
        }
        throw new AssertionError("No element with " + field + "=" + value + " in " + array);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
