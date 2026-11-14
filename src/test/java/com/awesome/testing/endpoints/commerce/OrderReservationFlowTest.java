package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.coupon.ApplyCouponDto;
import com.awesome.testing.dto.coupon.CouponDto;
import com.awesome.testing.dto.coupon.CouponRequestDto;
import com.awesome.testing.dto.giftcard.GiftCardDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.order.UtcMillis;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.coupon.CouponType;
import com.awesome.testing.entity.inventory.InventoryMovementEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.RecordedCommerceEvents;
import com.awesome.testing.repository.inventory.InventoryMovementRepository;
import com.awesome.testing.service.reservation.ReservationExpiryJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.awesome.testing.event.CommerceEventType.ORDER_CANCELLED;
import static com.awesome.testing.event.CommerceEventType.ORDER_CREATED;
import static com.awesome.testing.event.CommerceEventType.ORDER_RESERVATION_EXPIRED;
import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stock reservations with a time limit: the reservation end on every order body, the admin time control,
 * the expiry job, and expiry behaving exactly like a customer cancellation.
 */
class OrderReservationFlowTest extends AbstractTenderFlowTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ADMIN_ORDERS = "/api/v1/admin/orders/";

    @Autowired
    private RecordedCommerceEvents recorded;
    @Autowired
    private ReservationExpiryJob job;
    @Autowired
    private InventoryMovementRepository movementRepository;

    @Test
    void newOrdersCarryTheSameReservationEndInEveryRepresentation() {
        addToCart(clientToken, product("12.50", 5), 2);

        JsonNode created = body(executePost(ORDERS_ENDPOINT, getRandomAddress(), getHeadersWith(clientToken), String.class));
        long id = created.get("id").asLong();
        JsonNode fetched = body(executeGet(ORDERS_ENDPOINT + "/" + id, getHeadersWith(clientToken), String.class));
        JsonNode adminFetched = body(executeGet(ORDERS_ENDPOINT + "/" + id, getHeadersWith(adminToken), String.class));
        JsonNode listed = only(body(executeGet(ORDERS_ENDPOINT, getHeadersWith(clientToken), String.class)), id);
        JsonNode adminListed = only(body(executeGet(ORDERS_ENDPOINT + "/admin?size=1000",
                getHeadersWith(adminToken), String.class)), id);

        // l20 compares whole bodies: the new field must not make POST, GET and list bodies differ.
        assertThat(fetched).isEqualTo(created);
        assertThat(adminFetched).isEqualTo(created);
        assertThat(listed).isEqualTo(created);
        assertThat(adminListed).isEqualTo(created);

        String expiresAt = created.get("reservationExpiresAt").asString();
        assertThat(expiresAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
        Instant createdAt = java.time.LocalDateTime.parse(created.get("createdAt").asString())
                .atZone(ZoneId.systemDefault()).toInstant();
        assertThat(Duration.between(createdAt, Instant.parse(expiresAt)))
                .isBetween(Duration.ofMinutes(30).minusSeconds(1), Duration.ofMinutes(30).plusSeconds(1));
        OrderEntity stored = orderRepository.findById(id).orElseThrow();
        assertThat(stored.getReservationExpiresAt()).isEqualTo(Instant.parse(expiresAt));
    }

    @Test
    void theInternalSnapshotCarriesTheStoredReservationEnd() {
        OrderDto order = order(clientToken, product("10.00", 5), 1);

        JsonNode snapshot = body(executeGet("/internal/v1/orders/" + order.getId(), serviceHeaders(PAYMENT_TOKEN), String.class));

        assertThat(snapshot.get("reservationExpiresAt").asString()).isEqualTo(order.getReservationExpiresAt());
    }

    @Test
    void ordersWithoutAReservationOmitTheField() {
        OrderDto order = order(clientToken, product("10.00", 5), 1);
        clearReservation(order.getId());

        JsonNode fetched = body(executeGet(ORDERS_ENDPOINT + "/" + order.getId(), getHeadersWith(clientToken), String.class));
        JsonNode listed = only(body(executeGet(ORDERS_ENDPOINT, getHeadersWith(clientToken), String.class)), order.getId());
        JsonNode snapshot = body(executeGet("/internal/v1/orders/" + order.getId(), serviceHeaders(PAYMENT_TOKEN), String.class));

        assertThat(fetched.has("reservationExpiresAt")).isFalse();
        assertThat(listed.has("reservationExpiresAt")).isFalse();
        assertThat(snapshot.has("reservationExpiresAt")).isFalse();
        assertThat(snapshot.has("couponCode")).as("other nullable snapshot fields keep their shape").isTrue();
    }

    @Test
    void adminExpiryCancelsTheOrderLikeACustomerCancelWithTheSystemActor() {
        recorded.clear();
        ProductEntity product = product("40.00", 5);
        String couponCode = "HOLD" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        CouponDto coupon = executePost("/api/v1/admin/coupons", CouponRequestDto.builder().code(couponCode)
                .type(CouponType.FIXED_AMOUNT).value(new BigDecimal("5.00")).build(), getHeadersWith(adminToken),
                CouponDto.class).getBody();
        GiftCardDto card = issueGiftCard("10.00");
        grantPoints(client.getUsername(), 1_000);
        addToCart(clientToken, product, 2);
        executePut("/api/v1/cart/coupon", new ApplyCouponDto(couponCode), getHeadersWith(clientToken), String.class);
        applyGiftCard(clientToken, card.getCode(), CartDto.class);
        applyPoints(clientToken, 500, CartDto.class);
        OrderDto order = checkout(clientToken);
        assertThat(stockOf(product)).isEqualTo(3);

        ResponseEntity<OrderDto> expired = expireNow(order.getId(), adminToken, OrderDto.class);

        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.OK);
        OrderDto body = expired.getBody();
        assertThat(body.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        Instant expiredAt = Instant.parse(body.getReservationExpiresAt());
        assertThat(expiredAt).isBefore(Instant.now().plusMillis(1))
                .isAfter(Instant.parse(order.getReservationExpiresAt()).minus(Duration.ofMinutes(31)));
        assertThat(stockOf(product)).isEqualTo(5);
        assertThat(balanceOf(card.getCode())).isEqualByComparingTo("10.00");
        assertThat(loyalty(clientToken).getBalance()).isEqualTo(1_000);
        assertThat(order.getCouponCode()).isEqualTo(couponCode);
        assertThat(couponRedemptions(coupon.getId())).isZero();
        InventoryMovementEntity restored = movementRepository
                .findByProductIdOrderByCreatedAtDescIdDesc(product.getId(), PageRequest.of(0, 10)).getContent().stream()
                .filter(movement -> movement.getType() == InventoryMovementType.ORDER_RESTORED)
                .findFirst().orElseThrow();
        assertThat(restored.getActor()).isEqualTo("system");
        assertThat(restored.getDelta()).isEqualTo(2);

        assertThat(recorded.typesForOrder(order.getId()))
                .containsExactly(ORDER_CREATED, ORDER_CANCELLED, ORDER_RESERVATION_EXPIRED);
        CommerceEvent cancelled = recorded.forOrder(order.getId()).get(1);
        assertThat(cancelled.data()).containsEntry("reason", "RESERVATION_EXPIRED");
        CommerceEvent reservationExpired = recorded.forOrder(order.getId()).get(2);
        assertThat(reservationExpired.data()).containsOnlyKeys("orderId", "expiredAt", "totalAmount");
        assertThat(reservationExpired.data()).containsEntry("orderId", order.getId())
                .containsEntry("expiredAt", body.getReservationExpiresAt());
        assertThat((BigDecimal) reservationExpired.data().get("totalAmount")).isEqualByComparingTo(order.getTotalAmount());
        assertThat(reservationExpired.id()).isNotEqualTo(cancelled.id());
    }

    @Test
    void adminExpiryAnswersEveryDocumentedStatus() {
        OrderDto order = order(clientToken, product("10.00", 5), 1);

        assertThat(expireNow(order.getId(), null, ErrorDto.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(expireNow(order.getId(), clientToken, ErrorDto.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(expireNow(Long.MAX_VALUE, adminToken, ErrorDto.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(expireNow(order.getId(), adminToken, OrderDto.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<ErrorDto> again = expireNow(order.getId(), adminToken, ErrorDto.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().getMessage()).isEqualTo("Only PENDING orders hold a reservation; order is CANCELLED");

        OrderDto paid = order(clientToken, product("10.00", 5), 1);
        markPaid(paid.getId());
        assertThat(expireNow(paid.getId(), adminToken, ErrorDto.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(orderRepository.findById(paid.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void aCaptureAfterTheReservationExpiredIsRejected() {
        OrderDto order = order(clientToken, product("10.00", 5), 1);
        expireNow(order.getId(), adminToken, OrderDto.class);

        ResponseEntity<ErrorDto> capture = postResult(order.getId(),
                captured(UUID.randomUUID(), UUID.randomUUID(), "10.00"), ErrorDto.class);

        assertThat(capture.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void theJobExpiresDuePendingOrdersOnlyAndOnce() {
        recorded.clear();
        ProductEntity product = product("10.00", 20);
        OrderDto due = order(clientToken, product, 1);
        OrderDto notDueYet = order(clientToken, product, 1);
        OrderDto legacy = order(clientToken, product, 1);
        OrderDto paid = order(clientToken, product, 1);
        OrderDto cancelled = order(clientToken, product, 1);
        markPaid(paid.getId());
        cancel(cancelled.getId(), clientToken, OrderDto.class);
        Instant past = Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.MILLIS);
        setReservation(due.getId(), past);
        setReservation(paid.getId(), past);
        setReservation(cancelled.getId(), past);
        clearReservation(legacy.getId());
        int stockBefore = stockOf(product);

        int first = job.expireDue();
        int second = job.expireDue();

        // Other test classes share the database, so count this class's orders rather than the batch.
        assertThat(first).isPositive();
        assertThat(second).isZero();
        assertThat(statusOf(due)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(statusOf(notDueYet)).isEqualTo(OrderStatus.PENDING);
        assertThat(statusOf(legacy)).isEqualTo(OrderStatus.PENDING);
        assertThat(statusOf(paid)).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(product)).isEqualTo(stockBefore + 1);
        assertThat(recorded.typesForOrder(due.getId()))
                .containsExactly(ORDER_CREATED, ORDER_CANCELLED, ORDER_RESERVATION_EXPIRED);
        assertThat(recorded.forOrder(due.getId()).get(2).data()).containsEntry("expiredAt", UtcMillis.format(past));
        assertThat(recorded.typesForOrder(cancelled.getId())).containsExactly(ORDER_CREATED, ORDER_CANCELLED);
        assertThat(recorded.forOrder(cancelled.getId()).get(1).data()).containsEntry("reason", "CUSTOMER");
        assertThat(recorded.typesForOrder(paid.getId())).doesNotContain(ORDER_CANCELLED);
    }

    @Test
    void customerAndAdminCancellationsCarryTheirReason() {
        recorded.clear();
        OrderDto byCustomer = order(clientToken, product("10.00", 5), 1);
        OrderDto byAdmin = order(clientToken, product("10.00", 5), 1);
        OrderDto byStatus = order(clientToken, product("10.00", 5), 1);

        cancel(byCustomer.getId(), clientToken, OrderDto.class);
        cancel(byAdmin.getId(), adminToken, OrderDto.class);
        executePut(ORDERS_ENDPOINT + "/" + byStatus.getId() + "/status", OrderStatus.CANCELLED,
                getHeadersWith(adminToken), OrderDto.class);

        assertThat(recorded.forOrder(byCustomer.getId()).getLast().data()).containsEntry("reason", "CUSTOMER");
        assertThat(recorded.forOrder(byAdmin.getId()).getLast().data()).containsEntry("reason", "ADMIN");
        assertThat(recorded.forOrder(byStatus.getId()).getLast().data()).containsEntry("reason", "ADMIN");
        assertThat(recorded.typesForOrder(byCustomer.getId())).doesNotContain(ORDER_RESERVATION_EXPIRED);
    }

    @Test
    void webhookEventTypesOfferTheReservationExpiredEvent() {
        JsonNode types = body(executeGet("/api/v1/webhooks/event-types", getHeadersWith(clientToken), String.class));

        assertThat(types.findValuesAsString("name")).contains("order.reservation-expired", "order.cancelled")
                .doesNotContain("card-refund.requested");
    }

    private <T> ResponseEntity<T> expireNow(Long orderId, String token, Class<T> type) {
        return executePost(ADMIN_ORDERS + orderId + "/expire-reservation", null,
                token == null ? getJsonOnlyHeaders() : getHeadersWith(token), type);
    }

    private OrderStatus statusOf(OrderDto order) {
        return orderRepository.findById(order.getId()).orElseThrow().getStatus();
    }

    private void setReservation(Long orderId, Instant expiresAt) {
        OrderEntity entity = orderRepository.findById(orderId).orElseThrow();
        entity.setReservationExpiresAt(expiresAt);
        orderRepository.save(entity);
    }

    private void clearReservation(Long orderId) {
        setReservation(orderId, null);
    }

    private int couponRedemptions(Long couponId) {
        return executeGet("/api/v1/admin/coupons/" + couponId, getHeadersWith(adminToken), CouponDto.class)
                .getBody().getRedemptionCount();
    }

    private static JsonNode only(JsonNode page, long id) {
        List<JsonNode> matches = page.get("content").valueStream().filter(order -> order.get("id").asLong() == id).toList();
        assertThat(matches).hasSize(1);
        return matches.getFirst();
    }

    private static JsonNode body(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
        return JSON.readTree(response.getBody());
    }
}
