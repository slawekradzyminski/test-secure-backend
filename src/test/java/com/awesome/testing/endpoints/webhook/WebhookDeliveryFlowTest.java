package com.awesome.testing.endpoints.webhook;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.webhook.WebhookDeliveryDto;
import com.awesome.testing.dto.webhook.WebhookDeliveryStatus;
import com.awesome.testing.dto.webhook.WebhookSubscriptionDto;
import com.awesome.testing.dto.webhook.WebhookUpdateDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.webhook.WebhookDeliveryEntity;
import com.awesome.testing.entity.webhook.WebhookSubscriptionEntity;
import com.awesome.testing.repository.webhook.WebhookDeliveryRepository;
import com.awesome.testing.repository.webhook.WebhookSubscriptionRepository;
import com.awesome.testing.service.webhook.HmacReference;
import com.awesome.testing.service.webhook.StubWebhookHostResolver;
import com.awesome.testing.service.webhook.WebhookRetryJob;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.awesome.testing.endpoints.webhook.WebhookApi.DELIVERY_PAGE;
import static com.awesome.testing.endpoints.webhook.WebhookApi.WEBHOOKS;
import static com.awesome.testing.endpoints.webhook.WebhookApi.subscription;
import static com.awesome.testing.endpoints.webhook.WebhookApi.subscriptionPath;
import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Webhook deliveries end to end against WireMock on localhost as the receiver: fan-out
 * from order events, signatures, retries with backoff, auto-disable, redelivery and the
 * delivery-time SSRF check.
 */
class WebhookDeliveryFlowTest extends AbstractEcommerceTest {

    private static final WireMockServer RECEIVER = new WireMockServer(options().dynamicPort());
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private StubWebhookHostResolver dns;

    @Autowired
    private WebhookDeliveryRepository deliveries;

    @Autowired
    private WebhookSubscriptionRepository subscriptions;

    @Autowired
    private WebhookRetryJob retryJob;

    private String clientToken;
    private String path;

    @BeforeAll
    static void startReceiver() {
        RECEIVER.start();
    }

    @AfterAll
    static void stopReceiver() {
        RECEIVER.stop();
    }

    @BeforeEach
    void setUpReceiver() {
        RECEIVER.resetAll();
        clientToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        path = "/hook-" + UUID.randomUUID();
        receiverReturns(200, "ok");
    }

    @Test
    void pingIsSignedWithTheSecretShownAtCreation() throws Exception {
        // given
        WebhookSubscriptionDto hook = subscribe(receiverUrl(), "order.created");

        // when
        ResponseEntity<WebhookDeliveryDto> ping = ping(hook.getId());

        // then
        assertThat(ping.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        WebhookDeliveryDto delivery = ping.getBody();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(delivery.getEventType()).isEqualTo("webhook.ping");
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastStatusCode()).isEqualTo(200);
        assertThat(delivery.getResponseSnippet()).isEqualTo("ok");
        assertThat(delivery.getDeliveredAt()).isNotNull();
        assertThat(delivery.getDurationMs()).isNotNegative();

        LoggedRequest request = onlyRequest();
        String signature = request.getHeader("X-Webhook-Signature");
        assertThat(HmacReference.verifies(signature, hook.getSecret(), request.getBodyAsString())).isTrue();
        assertThat(HmacReference.verifies(signature, "whsec_wrong", request.getBodyAsString())).isFalse();
        long timestamp = Long.parseLong(signature.substring(2, signature.indexOf(',')));
        assertThat(timestamp).isCloseTo(Instant.now().getEpochSecond(), within(60L));
        assertThat(request.getHeader("X-Webhook-Id")).isEqualTo(delivery.getId().toString());
        assertThat(request.getHeader("X-Webhook-Event")).isEqualTo("webhook.ping");
        assertThat(request.getHeader("User-Agent")).isEqualTo("AwesomeTesting-Webhooks/1.0");
        assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(request.getBodyAsString()).isEqualTo(delivery.getPayload());
        JsonNode body = JSON.readTree(request.getBodyAsString());
        assertThat(body.path("id").asText()).isEqualTo(delivery.getEventId().toString());
        assertThat(body.path("type").asText()).isEqualTo("webhook.ping");
        assertThat(body.path("data").path("subscriptionId").asLong()).isEqualTo(hook.getId());
    }

    @Test
    void ordersFanOutToSubscribersOfTheirEventTypesOnly() throws Exception {
        // given
        WebhookSubscriptionDto everything = subscribe(receiverUrl(), "order.created", "order.cancelled");
        WebhookSubscriptionDto cancellations = subscribe(receiverUrl(), "order.cancelled");
        String otherToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        WebhookSubscriptionDto foreign = subscribeAs(otherToken, receiverUrl(), "order.created");
        ProductEntity product = product("20.00");

        // when
        OrderDto order = order(product, 2);
        ResponseEntity<OrderDto> cancel = executePost("/api/v1/orders/" + order.getId() + "/cancel", null,
                getHeadersWith(clientToken), OrderDto.class);

        // then
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<WebhookDeliveryDto> forEverything = deliveriesOf(everything.getId(), clientToken);
        assertThat(forEverything).extracting(WebhookDeliveryDto::getEventType)
                .containsExactly("order.cancelled", "order.created");
        assertThat(forEverything).allSatisfy(delivery ->
                assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED));
        assertThat(deliveriesOf(cancellations.getId(), clientToken)).extracting(WebhookDeliveryDto::getEventType)
                .containsExactly("order.cancelled");
        assertThat(deliveriesOf(foreign.getId(), otherToken)).isEmpty();

        List<LoggedRequest> requests = RECEIVER.findAll(postRequestedFor(urlEqualTo(path)));
        assertThat(requests).hasSize(3);
        LoggedRequest created = requests.stream()
                .filter(request -> "order.created".equals(request.getHeader("X-Webhook-Event"))).findFirst().orElseThrow();
        assertThat(HmacReference.verifies(created.getHeader("X-Webhook-Signature"), everything.getSecret(),
                created.getBodyAsString())).isTrue();
        JsonNode body = JSON.readTree(created.getBodyAsString());
        assertThat(body.path("type").asText()).isEqualTo("order.created");
        assertThat(body.path("data").path("orderId").asLong()).isEqualTo(order.getId());
        assertThat(body.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("data").path("totalAmount").decimalValue()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(body.path("createdAt").asText()).endsWith("Z");
        List<String> fieldNames = new java.util.ArrayList<>();
        body.fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames).containsExactly("id", "type", "createdAt", "data");

        LoggedRequest cancelledForSecond = requests.stream()
                .filter(request -> request.getBodyAsString().contains("order.cancelled"))
                .filter(request -> HmacReference.verifies(request.getHeader("X-Webhook-Signature"),
                        cancellations.getSecret(), request.getBodyAsString()))
                .findFirst().orElseThrow();
        assertThat(JSON.readTree(cancelledForSecond.getBodyAsString()).path("data").path("status").asText())
                .isEqualTo("CANCELLED");
    }

    @Test
    void failedDeliveriesAreRetriedWithBackoffUntilTheyArrive() {
        // given
        receiverReturns(500, "boom");
        WebhookSubscriptionDto hook = subscribe(receiverUrl(), "order.created");

        // when
        WebhookDeliveryDto first = ping(hook.getId()).getBody();

        // then
        assertThat(first.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastStatusCode()).isEqualTo(500);
        assertThat(first.getLastError()).isEqualTo("HTTP 500");
        assertThat(first.getResponseSnippet()).isEqualTo("boom");
        assertThat(Duration.between(Instant.now(), first.getNextAttemptAt()))
                .isBetween(Duration.ofSeconds(50), Duration.ofSeconds(61));
        assertThat(subscriptions.findById(hook.getId()).orElseThrow().getConsecutiveFailures()).isEqualTo(1);

        // when: the retry is not due yet
        retryJob.retryDueDeliveries();

        // then
        assertThat(RECEIVER.findAll(postRequestedFor(urlEqualTo(path)))).hasSize(1);

        // when: it is due and still failing
        makeDue(first.getId());
        retryJob.retryDueDeliveries();
        WebhookDeliveryDto second = delivery(hook.getId(), first.getId());

        // then
        assertThat(second.getAttempts()).isEqualTo(2);
        assertThat(Duration.between(Instant.now(), second.getNextAttemptAt()))
                .isBetween(Duration.ofSeconds(110), Duration.ofSeconds(121));

        // when: the receiver recovers
        receiverReturns(200, "fine");
        makeDue(first.getId());
        retryJob.retryDueDeliveries();
        WebhookDeliveryDto third = delivery(hook.getId(), first.getId());

        // then
        assertThat(third.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(third.getAttempts()).isEqualTo(3);
        assertThat(third.getNextAttemptAt()).isNull();
        assertThat(third.getLastError()).isNull();
        assertThat(subscriptions.findById(hook.getId()).orElseThrow().getConsecutiveFailures()).isZero();
        assertThat(RECEIVER.findAll(postRequestedFor(urlEqualTo(path))))
                .extracting(request -> request.getHeader("X-Webhook-Id")).containsOnly(first.getId().toString());
    }

    @Test
    void deliveriesFailForGoodAfterEightAttempts() {
        // given
        receiverReturns(503, "");
        WebhookSubscriptionDto hook = subscribe(receiverUrl(), "order.created");
        UUID id = ping(hook.getId()).getBody().getId();

        // when
        for (int attempt = 2; attempt <= 9; attempt++) {
            makeDue(id);
            retryJob.retryDueDeliveries();
        }

        // then
        WebhookDeliveryDto delivery = delivery(hook.getId(), id);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(8);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(RECEIVER.findAll(postRequestedFor(urlEqualTo(path)))).hasSize(8);
        PageDto<WebhookDeliveryDto> failed = executeGet(subscriptionPath(hook.getId()) + "/deliveries?status=FAILED",
                getHeadersWith(clientToken), DELIVERY_PAGE).getBody();
        PageDto<WebhookDeliveryDto> delivered = executeGet(subscriptionPath(hook.getId()) + "/deliveries?status=DELIVERED",
                getHeadersWith(clientToken), DELIVERY_PAGE).getBody();
        assertThat(failed.getContent()).extracting(WebhookDeliveryDto::getId).containsExactly(id);
        assertThat(delivered.getContent()).isEmpty();
    }

    @Test
    void twentyConsecutiveFailuresDisableTheSubscriptionUntilItIsReenabled() {
        // given
        receiverReturns(500, "");
        WebhookSubscriptionDto hook = subscribe(receiverUrl(), "order.created");
        WebhookSubscriptionEntity entity = subscriptions.findById(hook.getId()).orElseThrow();
        entity.setConsecutiveFailures(19);
        subscriptions.save(entity);

        // when
        WebhookDeliveryDto failed = ping(hook.getId()).getBody();
        WebhookSubscriptionDto disabled = executeGet(subscriptionPath(hook.getId()), getHeadersWith(clientToken),
                WebhookSubscriptionDto.class).getBody();
        makeDue(failed.getId());
        retryJob.retryDueDeliveries();
        ResponseEntity<ErrorDto> blockedPing = executePost(subscriptionPath(hook.getId()) + "/test", null,
                getHeadersWith(clientToken), ErrorDto.class);
        OrderDto order = order(product("5.00"), 1);

        // then
        assertThat(disabled.isActive()).isFalse();
        assertThat(disabled.getConsecutiveFailures()).isEqualTo(20);
        assertThat(disabled.getDisabledReason())
                .isEqualTo("Disabled after 20 consecutive failed deliveries; last error: HTTP 500");
        WebhookDeliveryDto abandoned = delivery(hook.getId(), failed.getId());
        assertThat(abandoned.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(abandoned.getLastError()).isEqualTo("Subscription is disabled");
        assertThat(blockedPing.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(order.getId()).isNotNull();
        assertThat(deliveriesOf(hook.getId(), clientToken)).hasSize(1);

        // when
        receiverReturns(200, "back");
        WebhookSubscriptionDto reenabled = executePut(subscriptionPath(hook.getId()), WebhookUpdateDto.builder()
                        .url(receiverUrl()).events(List.of("order.created")).active(true).build(),
                getHeadersWith(clientToken), WebhookSubscriptionDto.class).getBody();

        // then
        assertThat(reenabled.isActive()).isTrue();
        assertThat(reenabled.getConsecutiveFailures()).isZero();
        assertThat(reenabled.getDisabledReason()).isNull();
        assertThat(ping(hook.getId()).getBody().getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
    }

    @Test
    void redeliverySendsTheSameEventAsANewDelivery() {
        // given
        WebhookSubscriptionDto hook = subscribe(receiverUrl(), "order.created");
        WebhookDeliveryDto original = ping(hook.getId()).getBody();

        // when
        ResponseEntity<WebhookDeliveryDto> redelivered = executePost(subscriptionPath(hook.getId()) + "/deliveries/"
                + original.getId() + "/redeliver", null, getHeadersWith(clientToken), WebhookDeliveryDto.class);

        // then
        assertThat(redelivered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        WebhookDeliveryDto copy = redelivered.getBody();
        assertThat(copy.getId()).isNotEqualTo(original.getId());
        assertThat(copy.getEventId()).isEqualTo(original.getEventId());
        assertThat(copy.getPayload()).isEqualTo(original.getPayload());
        assertThat(copy.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(delivery(hook.getId(), original.getId()).getAttempts()).isEqualTo(1);
        List<LoggedRequest> requests = RECEIVER.findAll(postRequestedFor(urlEqualTo(path)));
        assertThat(requests).extracting(request -> request.getHeader("X-Webhook-Id"))
                .containsExactly(original.getId().toString(), copy.getId().toString());
        assertThat(requests).extracting(LoggedRequest::getBodyAsString).containsOnly(original.getPayload());
        assertThat(deliveriesOf(hook.getId(), clientToken)).hasSize(2);
    }

    @Test
    void deliveriesConnectToTheVettedAddressAndKeepTheHostName() {
        // given
        String host = "hooks-" + UUID.randomUUID() + ".example.test";
        dns.register(host, "127.0.0.1");
        WebhookSubscriptionDto hook = subscribe("http://" + host + ":" + RECEIVER.port() + path, "order.created");

        // when
        WebhookDeliveryDto delivery = ping(hook.getId()).getBody();

        // then
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(onlyRequest().getHeader("Host")).isEqualTo(host + ":" + RECEIVER.port());
    }

    @Test
    void dnsRebindingToAPrivateAddressIsBlockedAtDeliveryTime() {
        // given: public when the subscription is created, private when the delivery is sent
        String host = "rebind-" + UUID.randomUUID() + ".example.test";
        dns.register(host, "93.184.215.14", "10.0.0.5");
        WebhookSubscriptionDto hook = subscribe("https://" + host + "/hook", "order.created");

        // when
        WebhookDeliveryDto delivery = ping(hook.getId()).getBody();

        // then
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getLastStatusCode()).isNull();
        assertThat(delivery.getLastError()).isEqualTo("Blocked: Host " + host
                + " resolves to a private address; webhooks can only call public addresses");
        assertThat(RECEIVER.getAllServeEvents()).isEmpty();
    }

    @Test
    void redirectsAreRecordedAsFailuresAndNeverFollowed() {
        // given
        RECEIVER.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(301)
                .withHeader("Location", "http://169.254.169.254/latest/meta-data/")));
        WebhookSubscriptionDto hook = subscribe(receiverUrl(), "order.created");

        // when
        WebhookDeliveryDto delivery = ping(hook.getId()).getBody();

        // then
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getLastStatusCode()).isEqualTo(301);
        assertThat(delivery.getLastError()).isEqualTo("HTTP 301 (redirects are not followed)");
        assertThat(RECEIVER.getAllServeEvents()).hasSize(1);
    }

    @Test
    void deliveriesArePagedNewestFirst() {
        // given
        WebhookSubscriptionDto hook = subscribe(receiverUrl(), "order.created");
        UUID first = ping(hook.getId()).getBody().getId();
        UUID second = ping(hook.getId()).getBody().getId();
        UUID third = ping(hook.getId()).getBody().getId();

        // when
        PageDto<WebhookDeliveryDto> page = executeGet(subscriptionPath(hook.getId()) + "/deliveries?page=0&size=2",
                getHeadersWith(clientToken), DELIVERY_PAGE).getBody();
        PageDto<WebhookDeliveryDto> last = executeGet(subscriptionPath(hook.getId()) + "/deliveries?page=1&size=2",
                getHeadersWith(clientToken), DELIVERY_PAGE).getBody();

        // then
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(WebhookDeliveryDto::getId).containsExactly(third, second);
        assertThat(last.getContent()).extracting(WebhookDeliveryDto::getId).containsExactly(first);
    }

    private void receiverReturns(int status, String body) {
        RECEIVER.resetMappings();
        RECEIVER.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(status).withBody(body)));
    }

    private String receiverUrl() {
        return "http://127.0.0.1:" + RECEIVER.port() + path;
    }

    private LoggedRequest onlyRequest() {
        List<LoggedRequest> requests = RECEIVER.findAll(postRequestedFor(urlEqualTo(path)));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }

    private WebhookSubscriptionDto subscribe(String url, String... events) {
        return subscribeAs(clientToken, url, events);
    }

    private WebhookSubscriptionDto subscribeAs(String token, String url, String... events) {
        ResponseEntity<WebhookSubscriptionDto> response = executePost(WEBHOOKS, subscription(url, events),
                getHeadersWith(token), WebhookSubscriptionDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    @Test
    void deliveriesSendTheRequestIdAndAChildTraceparentOfTheQueuingRequest() {
        // given
        WebhookSubscriptionDto hook = subscribe(receiverUrl(), "order.created");
        org.springframework.http.HttpHeaders headers = getHeadersWith(clientToken);
        headers.add("X-Request-Id", "ping-" + path.substring(6));
        headers.add("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        // when
        ResponseEntity<WebhookDeliveryDto> ping = executePost(subscriptionPath(hook.getId()) + "/test", null, headers,
                WebhookDeliveryDto.class);

        // then
        assertThat(ping.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        LoggedRequest request = onlyRequest();
        assertThat(request.getHeader("X-Request-Id")).isEqualTo("ping-" + path.substring(6));
        assertThat(request.getHeader("X-Webhook-Id")).isEqualTo(ping.getBody().getId().toString());
        assertThat(request.getHeader("traceparent"))
                .matches("00-0af7651916cd43dd8448eb211c80319c-[0-9a-f]{16}-01")
                .doesNotContain("b7ad6b7169203331");
        WebhookDeliveryEntity stored = deliveries.findById(ping.getBody().getId()).orElseThrow();
        assertThat(stored.getRequestId()).isEqualTo("ping-" + path.substring(6));
        assertThat(stored.getTraceparent()).startsWith("00-0af7651916cd43dd8448eb211c80319c-");
    }

    private ResponseEntity<WebhookDeliveryDto> ping(Long id) {
        return executePost(subscriptionPath(id) + "/test", null, getHeadersWith(clientToken), WebhookDeliveryDto.class);
    }

    private WebhookDeliveryDto delivery(Long subscriptionId, UUID deliveryId) {
        ResponseEntity<WebhookDeliveryDto> response = executeGet(subscriptionPath(subscriptionId) + "/deliveries/" + deliveryId,
                getHeadersWith(clientToken), WebhookDeliveryDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<WebhookDeliveryDto> deliveriesOf(Long subscriptionId, String token) {
        ResponseEntity<PageDto<WebhookDeliveryDto>> response = executeGet(subscriptionPath(subscriptionId) + "/deliveries",
                getHeadersWith(token), DELIVERY_PAGE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getContent();
    }

    private void makeDue(UUID deliveryId) {
        WebhookDeliveryEntity delivery = deliveries.findById(deliveryId).orElseThrow();
        if (delivery.getNextAttemptAt() != null) {
            delivery.setNextAttemptAt(Instant.now().minusSeconds(1));
            deliveries.save(delivery);
        }
    }

    private ProductEntity product(String price) {
        return productRepository.save(ProductEntity.builder()
                .name("Webhook product " + System.nanoTime())
                .description("Product used by webhook tests")
                .price(new BigDecimal(price))
                .stockQuantity(10)
                .category("Tests")
                .build());
    }

    private OrderDto order(ProductEntity product, int quantity) {
        ResponseEntity<String> cart = executePost(CART_ITEMS_ENDPOINT,
                CartItemDto.builder().productId(product.getId()).quantity(quantity).build(),
                getHeadersWith(clientToken), String.class);
        assertThat(cart.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<OrderDto> order = executePost(ORDERS_ENDPOINT, getRandomAddress(), getHeadersWith(clientToken),
                OrderDto.class);
        assertThat(order.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return order.getBody();
    }
}
