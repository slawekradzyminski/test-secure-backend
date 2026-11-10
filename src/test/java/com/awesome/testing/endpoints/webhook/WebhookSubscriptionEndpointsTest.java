package com.awesome.testing.endpoints.webhook;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.webhook.WebhookCreateDto;
import com.awesome.testing.dto.webhook.WebhookDeliveryDto;
import com.awesome.testing.dto.webhook.WebhookEventTypeDto;
import com.awesome.testing.dto.webhook.WebhookSubscriptionDto;
import com.awesome.testing.dto.webhook.WebhookUpdateDto;
import com.awesome.testing.repository.webhook.WebhookSubscriptionRepository;
import com.awesome.testing.service.webhook.StubWebhookHostResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.awesome.testing.endpoints.webhook.WebhookApi.ADMIN_WEBHOOKS;
import static com.awesome.testing.endpoints.webhook.WebhookApi.SUBSCRIPTIONS;
import static com.awesome.testing.endpoints.webhook.WebhookApi.SUBSCRIPTION_PAGE;
import static com.awesome.testing.endpoints.webhook.WebhookApi.WEBHOOKS;
import static com.awesome.testing.endpoints.webhook.WebhookApi.subscription;
import static com.awesome.testing.endpoints.webhook.WebhookApi.subscriptionPath;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

/** Webhook subscription CRUD per status code; no deliveries leave the process here. */
class WebhookSubscriptionEndpointsTest extends DomainHelper {

    private static final String PUBLIC_URL = "https://hooks.crud.example.com/shop";

    @Autowired
    private StubWebhookHostResolver dns;

    @Autowired
    private WebhookSubscriptionRepository subscriptions;

    private String clientName;
    private String clientToken;
    private String otherToken;

    @BeforeEach
    void createUsers() {
        var client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        clientName = client.getUsername();
        clientToken = getToken(client);
        otherToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        dns.register("hooks.crud.example.com", "93.184.215.14");
    }

    @Test
    void createReturnsTheSecretOnceAndStoresItEncrypted() {
        // when
        ResponseEntity<WebhookSubscriptionDto> created = create(clientToken,
                subscription(PUBLIC_URL, "order.created", "order.cancelled", "order.created"));

        // then
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        WebhookSubscriptionDto body = created.getBody();
        assertThat(body.getSecret()).matches("whsec_[A-Za-z0-9_-]{43}");
        assertThat(body.getEvents()).containsExactly("order.created", "order.cancelled");
        assertThat(body.getUsername()).isEqualTo(clientName);
        assertThat(body.isActive()).isTrue();
        assertThat(body.getDescription()).isEqualTo("test hook");
        assertThat(subscriptions.findById(body.getId()).orElseThrow().getSecret())
                .isNotBlank().doesNotContain(body.getSecret());

        ResponseEntity<String> fetched = executeGet(subscriptionPath(body.getId()), getHeadersWith(clientToken), String.class);
        ResponseEntity<String> listed = executeGet(WEBHOOKS, getHeadersWith(clientToken), String.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).doesNotContain("secret").doesNotContain(body.getSecret());
        assertThat(listed.getBody()).doesNotContain("secret").contains(PUBLIC_URL);
    }

    @Test
    void endpointsRequireAuthentication() {
        assertThat(executeGet(WEBHOOKS, getJsonOnlyHeaders(), ErrorDto.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(executePost(WEBHOOKS, subscription(PUBLIC_URL, "order.created"), getJsonOnlyHeaders(), ErrorDto.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(executeGet(WEBHOOKS + "/event-types", getJsonOnlyHeaders(), ErrorDto.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listsEventTypes() {
        // when
        ResponseEntity<List<WebhookEventTypeDto>> response = executeGet(WEBHOOKS + "/event-types",
                getHeadersWith(clientToken), new ParameterizedTypeReference<>() {
                });

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(WebhookEventTypeDto::name)
                .contains("order.created", "order.cancelled", "refund.booked", "webhook.ping");
    }

    @Test
    void rejectsInvalidBodies() {
        // when
        ResponseEntity<Map<String, String>> blank = executePost(WEBHOOKS,
                WebhookCreateDto.builder().url(" ").events(List.of()).build(), getHeadersWith(clientToken),
                new ParameterizedTypeReference<>() {
                });
        ResponseEntity<ErrorDto> unknownEvent = create(clientToken, subscription(PUBLIC_URL, "order.exploded"), ErrorDto.class);

        // then
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getBody()).containsEntry("url", "URL is required")
                .containsEntry("events", "At least one event type is required");
        assertThat(unknownEvent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknownEvent.getBody().getMessage()).startsWith("Unknown event type 'order.exploded'. Allowed:");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "http://10.0.0.1/hook|Webhook URL rejected: Host 10.0.0.1 resolves to a private address; webhooks can only call public addresses",
            "http://169.254.169.254/latest/meta-data/|Webhook URL rejected: Host 169.254.169.254 resolves to a link-local address; webhooks can only call public addresses",
            "http://192.168.0.10/|Webhook URL rejected: Host 192.168.0.10 resolves to a private address; webhooks can only call public addresses",
            "http://[fd00::1]/|Webhook URL rejected: Host fd00::1 resolves to a unique-local address; webhooks can only call public addresses",
            "http://localhost:4001/|Webhook URL rejected: Host localhost is internal; webhooks can only call public hosts",
            "http://backend:4001/api|Webhook URL rejected: Host backend is not a fully qualified domain name; webhooks can only call public hosts",
            "https://hooks.crud.example.com:8080/|Webhook URL rejected: URL port 8080 is not allowed; use 80 or 443",
            "ftp://hooks.crud.example.com/|Webhook URL rejected: URL must use http or https",
            "https://admin:pw@hooks.crud.example.com/|Webhook URL rejected: URL must not contain credentials",
            "inbox://not-my-inbox|Webhook URL rejected: unknown inbox; create one with POST /api/v1/webhook-inboxes"})
    void rejectsUnsafeTargetsWithAClearMessage(String url, String message) {
        // when
        ResponseEntity<ErrorDto> response = create(clientToken, subscription(url, "order.created"), ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo(message);
    }

    @Test
    void limitsSubscriptionsPerUser() {
        // given
        for (int i = 0; i < 5; i++) {
            assertThat(create(clientToken, subscription(PUBLIC_URL, "order.created")).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }

        // when
        ResponseEntity<ErrorDto> sixth = create(clientToken, subscription(PUBLIC_URL, "order.created"), ErrorDto.class);

        // then
        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(sixth.getBody().getMessage()).isEqualTo("You can have at most 5 webhook subscriptions");
        assertThat(create(otherToken, subscription(PUBLIC_URL, "order.created")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void ownersUpdateRotateAndDelete() {
        // given
        WebhookSubscriptionDto created = create(clientToken, subscription(PUBLIC_URL, "order.created")).getBody();
        WebhookUpdateDto update = WebhookUpdateDto.builder().url(PUBLIC_URL + "/v2").events(List.of("refund.booked"))
                .description(null).active(false).build();

        // when
        ResponseEntity<WebhookSubscriptionDto> updated = executePut(subscriptionPath(created.getId()), update,
                getHeadersWith(clientToken), WebhookSubscriptionDto.class);
        ResponseEntity<WebhookSubscriptionDto> rotated = executePost(subscriptionPath(created.getId()) + "/rotate-secret",
                null, getHeadersWith(clientToken), WebhookSubscriptionDto.class);
        ResponseEntity<Void> deleted = executeDelete(subscriptionPath(created.getId()), getHeadersWith(clientToken), Void.class);
        ResponseEntity<ErrorDto> afterDelete = executeGet(subscriptionPath(created.getId()), getHeadersWith(clientToken),
                ErrorDto.class);

        // then
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getUrl()).isEqualTo(PUBLIC_URL + "/v2");
        assertThat(updated.getBody().getEvents()).containsExactly("refund.booked");
        assertThat(updated.getBody().getDescription()).isNull();
        assertThat(updated.getBody().isActive()).isFalse();
        assertThat(updated.getBody().getSecret()).isNull();
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rotated.getBody().getSecret()).matches("whsec_[A-Za-z0-9_-]{43}").isNotEqualTo(created.getSecret());
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(afterDelete.getBody().getMessage()).isEqualTo("Webhook subscription not found");
    }

    @Test
    void updatesAreValidatedLikeCreates() {
        // given
        WebhookSubscriptionDto created = create(clientToken, subscription(PUBLIC_URL, "order.created")).getBody();
        WebhookUpdateDto privateTarget = WebhookUpdateDto.builder().url("https://10.20.30.40/")
                .events(List.of("order.created")).build();

        // when
        ResponseEntity<ErrorDto> rejected = executePut(subscriptionPath(created.getId()), privateTarget,
                getHeadersWith(clientToken), ErrorDto.class);

        // then
        assertThat(subscriptions.findById(created.getId()).orElseThrow().getUrl()).isEqualTo(PUBLIC_URL);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().getMessage()).contains("a private address");
    }

    @Test
    void otherUsersGetForbiddenAndUnknownIdsNotFound() {
        // given
        WebhookSubscriptionDto created = create(clientToken, subscription(PUBLIC_URL, "order.created")).getBody();
        String path = subscriptionPath(created.getId());
        WebhookUpdateDto update = WebhookUpdateDto.builder().url(PUBLIC_URL).events(List.of("order.created")).build();

        // when / then
        assertForbidden(executeGet(path, getHeadersWith(otherToken), ErrorDto.class));
        assertForbidden(executePut(path, update, getHeadersWith(otherToken), ErrorDto.class));
        assertForbidden(executeDelete(path, getHeadersWith(otherToken), ErrorDto.class));
        assertForbidden(executePost(path + "/rotate-secret", null, getHeadersWith(otherToken), ErrorDto.class));
        assertForbidden(executePost(path + "/test", null, getHeadersWith(otherToken), ErrorDto.class));
        assertForbidden(executeGet(path + "/deliveries", getHeadersWith(otherToken), ErrorDto.class));
        assertForbidden(executeGet(path + "/deliveries/" + UUID.randomUUID(), getHeadersWith(otherToken), ErrorDto.class));
        assertForbidden(executePost(path + "/deliveries/" + UUID.randomUUID() + "/redeliver", null,
                getHeadersWith(otherToken), ErrorDto.class));
        assertThat(executeGet(subscriptionPath(Long.MAX_VALUE), getHeadersWith(clientToken), ErrorDto.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<ErrorDto> unknownDelivery = executeGet(path + "/deliveries/" + UUID.randomUUID(),
                getHeadersWith(clientToken), ErrorDto.class);
        assertThat(unknownDelivery.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknownDelivery.getBody().getMessage()).isEqualTo("Webhook delivery not found");
        assertThat(executePost(path + "/deliveries/" + UUID.randomUUID() + "/redeliver", null,
                getHeadersWith(clientToken), ErrorDto.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(executeGet(path + "/deliveries/not-a-uuid", getHeadersWith(clientToken), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void disabledSubscriptionsCannotBePingedUntilReenabled() {
        // given
        WebhookSubscriptionDto created = create(clientToken, subscription(PUBLIC_URL, "order.created")).getBody();
        executePut(subscriptionPath(created.getId()), WebhookUpdateDto.builder().url(PUBLIC_URL)
                .events(List.of("order.created")).active(false).build(), getHeadersWith(clientToken), String.class);

        // when
        ResponseEntity<ErrorDto> ping = executePost(subscriptionPath(created.getId()) + "/test", null,
                getHeadersWith(clientToken), ErrorDto.class);

        // then
        assertThat(ping.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ping.getBody().getMessage()).isEqualTo("Webhook subscription is disabled; enable it with PUT first");
    }

    @Test
    void adminsListEverySubscriptionAndClientsAreForbidden() {
        // given
        create(clientToken, subscription(PUBLIC_URL, "order.created"));
        String adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));

        // when
        ResponseEntity<PageDto<WebhookSubscriptionDto>> filtered = executeGet(ADMIN_WEBHOOKS + "?username=" + clientName,
                getHeadersWith(adminToken), SUBSCRIPTION_PAGE);
        ResponseEntity<PageDto<WebhookSubscriptionDto>> all = executeGet(ADMIN_WEBHOOKS + "?size=500&page=-1",
                getHeadersWith(adminToken), SUBSCRIPTION_PAGE);
        ResponseEntity<ErrorDto> forbidden = executeGet(ADMIN_WEBHOOKS, getHeadersWith(clientToken), ErrorDto.class);

        // then
        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filtered.getBody().getContent()).singleElement().satisfies(dto -> {
            assertThat(dto.getUsername()).isEqualTo(clientName);
            assertThat(dto.getSecret()).isNull();
        });
        assertThat(all.getBody().getPageSize()).isEqualTo(100);
        assertThat(all.getBody().getPageNumber()).isZero();
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(executeGet(ADMIN_WEBHOOKS, getJsonOnlyHeaders(), ErrorDto.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listShowsOnlyOwnSubscriptions() {
        // given
        create(clientToken, subscription(PUBLIC_URL, "order.created"));
        create(otherToken, subscription(PUBLIC_URL, "order.paid"));

        // when
        ResponseEntity<List<WebhookSubscriptionDto>> mine = executeGet(WEBHOOKS, getHeadersWith(clientToken), SUBSCRIPTIONS);

        // then
        assertThat(mine.getBody()).singleElement().satisfies(dto -> assertThat(dto.getEvents()).containsExactly("order.created"));
    }

    @Test
    void deliveryListOfANewSubscriptionIsEmpty() {
        // given
        WebhookSubscriptionDto created = create(clientToken, subscription(PUBLIC_URL, "order.created")).getBody();

        // when
        ResponseEntity<PageDto<WebhookDeliveryDto>> page = executeGet(subscriptionPath(created.getId()) + "/deliveries",
                getHeadersWith(clientToken), WebhookApi.DELIVERY_PAGE);

        // then
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody().getTotalElements()).isZero();
    }

    private ResponseEntity<WebhookSubscriptionDto> create(String token, WebhookCreateDto request) {
        return create(token, request, WebhookSubscriptionDto.class);
    }

    private <T> ResponseEntity<T> create(String token, WebhookCreateDto request, Class<T> type) {
        return executePost(WEBHOOKS, request, getHeadersWith(token), type);
    }

    private static void assertForbidden(ResponseEntity<ErrorDto> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("You cannot access someone else's webhook subscription");
    }
}
