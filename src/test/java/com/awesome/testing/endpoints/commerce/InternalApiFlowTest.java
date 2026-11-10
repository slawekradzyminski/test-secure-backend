package com.awesome.testing.endpoints.commerce;

import java.math.BigDecimal;

import com.awesome.testing.internal.dto.OrderRefundDto;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.awesome.testing.internal.dto.FulfilmentUpdateDto;
import com.awesome.testing.internal.dto.InternalNotificationDto;
import com.awesome.testing.internal.dto.InternalOrderDto;
import com.awesome.testing.internal.dto.InternalProductDto;
import com.awesome.testing.internal.dto.InventoryReceiptDto;
import com.awesome.testing.internal.dto.InventoryReceiptLineDto;
import com.awesome.testing.internal.dto.InventoryReceiptResultDto;
import com.awesome.testing.repository.EmailEventRepository;
import com.awesome.testing.repository.inventory.InventoryMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

/** The /internal/v1 API that warehouse-service calls with X-Service-Token. */
class InternalApiFlowTest extends AbstractCommerceFlowTest {

    private static final String INTERNAL = "/internal/v1";
    private static final String TOKEN = "test-warehouse-token";

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private EmailEventRepository emailEventRepository;

    @Test
    void missingOrWrongServiceTokenIsRejectedEvenWithAnAdminJwt() {
        ResponseEntity<ErrorDto> missing = executeGet(INTERNAL + "/products", getJsonOnlyHeaders(), ErrorDto.class);
        ResponseEntity<ErrorDto> wrong = executeGet(INTERNAL + "/products", serviceHeaders("wrong-token"), ErrorDto.class);
        ResponseEntity<ErrorDto> jwtOnly = executeGet(INTERNAL + "/products", getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<ErrorDto> prefix = executeGet(INTERNAL + "/products", serviceHeaders(TOKEN + "x"), ErrorDto.class);

        assertThat(List.of(missing, wrong, jwtOnly, prefix)).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().getMessage()).isEqualTo("Invalid service token");
        });
    }

    @Test
    void thePaymentServiceTokenIsRejectedByTheWarehouseEndpoints() {
        ResponseEntity<ErrorDto> products = executeGet(INTERNAL + "/products", serviceHeaders("test-payment-token"), ErrorDto.class);
        ResponseEntity<ErrorDto> notification = executePost(INTERNAL + "/notifications", "{}",
                serviceHeaders("test-payment-token"), ErrorDto.class);

        assertThat(List.of(products, notification)).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().getMessage()).isEqualTo("Invalid service token");
        });
    }

    @Test
    void validTokenWorksWithoutAJwtAndGetsNoCorsHeaders() {
        HttpHeaders headers = serviceHeaders(TOKEN);
        headers.add(HttpHeaders.ORIGIN, "http://localhost:8081");

        HttpHeaders publicHeaders = getHeadersWith(clientToken);
        publicHeaders.add(HttpHeaders.ORIGIN, "http://localhost:8081");

        ResponseEntity<String> response = executeGet(INTERNAL + "/products", headers, String.class);
        ResponseEntity<String> publicResponse = executeGet(PRODUCTS_ENDPOINT, publicHeaders, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
        assertThat(publicResponse.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:8081");
    }

    @Test
    void orderSnapshotAndQueueListPaidUnreleasedOrdersOldestFirst() {
        ProductEntity product = product("25.00", 10);
        OrderDto first = order(clientToken, product, 2);
        OrderDto second = order(clientToken, product, 1);
        OrderDto pending = order(clientToken, product, 1);
        markPaid(first.getId());
        markPaid(second.getId());

        InternalOrderDto snapshot = executeGet(INTERNAL + "/orders/" + first.getId(), serviceHeaders(TOKEN),
                InternalOrderDto.class).getBody();
        PageDto<InternalOrderDto> queue = queue("?status=PAID&fulfilment=NONE&page=0&size=20");
        PageDto<InternalOrderDto> clampedHigh = queue("?size=1000");
        PageDto<InternalOrderDto> clampedLow = queue("?size=0&page=-3");
        ResponseEntity<ErrorDto> missing = executeGet(INTERNAL + "/orders/999999", serviceHeaders(TOKEN), ErrorDto.class);

        assertThat(snapshot.username()).isEqualTo(client.getUsername());
        assertThat(snapshot.status()).isEqualTo(OrderStatus.PAID);
        assertThat(snapshot.fulfilmentState()).isEqualTo(FulfilmentState.NONE);
        assertThat(snapshot.totalAmount()).isEqualByComparingTo("50.00");
        assertThat(snapshot.subtotalAmount()).isEqualByComparingTo("50.00");
        assertThat(snapshot.shippingAddress().getCity()).isNotBlank();
        assertThat(snapshot.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(product.getId());
            assertThat(item.productName()).isEqualTo(product.getName());
            assertThat(item.category()).isEqualTo("Tests");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.unitPrice()).isEqualByComparingTo("25.00");
        });
        assertThat(queue.getContent()).extracting(InternalOrderDto::id).containsExactly(first.getId(), second.getId())
                .doesNotContain(pending.getId());
        assertThat(clampedHigh.getPageSize()).isEqualTo(100);
        assertThat(clampedLow.getPageSize()).isEqualTo(1);
        assertThat(clampedLow.getPageNumber()).isZero();
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void fulfilmentMovesForwardOnlyAndShippedAndDeliveredSetTheOrderStatus() {
        OrderDto order = order(clientToken, product("25.00", 10), 1);
        ResponseEntity<ErrorDto> pendingRelease = fulfilment(order.getId(), FulfilmentState.AWAITING_PICK, ErrorDto.class);
        markPaid(order.getId());

        InternalOrderDto awaiting = fulfilment(order.getId(), FulfilmentState.AWAITING_PICK, InternalOrderDto.class).getBody();
        ResponseEntity<InternalOrderDto> retriedRelease = fulfilment(order.getId(), FulfilmentState.AWAITING_PICK, InternalOrderDto.class);
        ResponseEntity<ErrorDto> skip = fulfilment(order.getId(), FulfilmentState.PACKED, ErrorDto.class);
        ResponseEntity<ErrorDto> cancelThroughPut = fulfilment(order.getId(), FulfilmentState.CANCELLED, ErrorDto.class);
        fulfilment(order.getId(), FulfilmentState.PICKED, InternalOrderDto.class);
        ResponseEntity<InternalOrderDto> retriedPick = fulfilment(order.getId(), FulfilmentState.PICKED, InternalOrderDto.class);
        ResponseEntity<ErrorDto> manualStatus = executePut(ORDERS_ENDPOINT + "/" + order.getId() + "/status",
                OrderStatus.SHIPPED, getHeadersWith(adminToken), ErrorDto.class);
        fulfilment(order.getId(), FulfilmentState.PACKED, InternalOrderDto.class);
        InternalOrderDto shipped = fulfilment(order.getId(), FulfilmentState.SHIPPED, InternalOrderDto.class).getBody();
        ResponseEntity<ErrorDto> backwards = fulfilment(order.getId(), FulfilmentState.PICKED, ErrorDto.class);
        InternalOrderDto delivered = fulfilment(order.getId(), FulfilmentState.DELIVERED, InternalOrderDto.class).getBody();
        OrderDto customerView = executeGet(ORDERS_ENDPOINT + "/" + order.getId(), getHeadersWith(clientToken), OrderDto.class).getBody();
        ResponseEntity<ErrorDto> unknownOrder = fulfilment(999999L, FulfilmentState.AWAITING_PICK, ErrorDto.class);

        assertThat(pendingRelease.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(awaiting.fulfilmentState()).isEqualTo(FulfilmentState.AWAITING_PICK);
        assertThat(awaiting.status()).isEqualTo(OrderStatus.PAID);
        assertThat(retriedRelease.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retriedRelease.getBody().fulfilmentState()).isEqualTo(FulfilmentState.AWAITING_PICK);
        assertThat(skip.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(skip.getBody().getMessage()).isEqualTo("Cannot change fulfilment state from AWAITING_PICK to PACKED");
        assertThat(cancelThroughPut.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(retriedPick.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retriedPick.getBody().fulfilmentState()).isEqualTo(FulfilmentState.PICKED);
        assertThat(manualStatus.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(backwards.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(delivered.fulfilmentState()).isEqualTo(FulfilmentState.DELIVERED);
        assertThat(customerView.getFulfilmentState()).isEqualTo(FulfilmentState.DELIVERED);
        assertThat(unknownOrder.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void customerCancellationFollowsTheFulfilmentState() {
        ProductEntity product = product("25.00", 10);
        OrderDto awaiting = order(clientToken, product, 2);
        markPaid(awaiting.getId());
        fulfilment(awaiting.getId(), FulfilmentState.AWAITING_PICK, InternalOrderDto.class);
        OrderDto picked = order(clientToken, product, 3);
        markPaid(picked.getId());
        fulfilment(picked.getId(), FulfilmentState.AWAITING_PICK, InternalOrderDto.class);
        fulfilment(picked.getId(), FulfilmentState.PICKED, InternalOrderDto.class);

        ResponseEntity<OrderDto> cancelAwaiting = cancel(awaiting.getId(), clientToken, OrderDto.class);
        ResponseEntity<ErrorDto> cancelPicked = cancel(picked.getId(), clientToken, ErrorDto.class);
        ResponseEntity<ErrorDto> adminCancelPicked = cancel(picked.getId(), adminToken, ErrorDto.class);
        ResponseEntity<ErrorDto> pickAfterCancel = fulfilment(awaiting.getId(), FulfilmentState.PICKED, ErrorDto.class);

        assertThat(cancelAwaiting.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelAwaiting.getBody().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelAwaiting.getBody().getFulfilmentState()).isEqualTo(FulfilmentState.CANCELLED);
        assertThat(cancelPicked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(adminCancelPicked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(pickAfterCancel.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stockOf(product)).isEqualTo(10 - 3);
    }

    @Test
    void warehouseCancelRestoresStockOnlyWhileAwaitingPickOrPicked() {
        ProductEntity product = product("25.00", 10);
        OrderDto unreleased = order(clientToken, product, 1);
        markPaid(unreleased.getId());
        OrderDto picked = order(clientToken, product, 2);
        markPaid(picked.getId());
        fulfilment(picked.getId(), FulfilmentState.AWAITING_PICK, InternalOrderDto.class);
        fulfilment(picked.getId(), FulfilmentState.PICKED, InternalOrderDto.class);
        OrderDto packed = order(clientToken, product, 3);
        markPaid(packed.getId());
        fulfilment(packed.getId(), FulfilmentState.AWAITING_PICK, InternalOrderDto.class);
        fulfilment(packed.getId(), FulfilmentState.PICKED, InternalOrderDto.class);
        fulfilment(packed.getId(), FulfilmentState.PACKED, InternalOrderDto.class);

        ResponseEntity<ErrorDto> cancelUnreleased = warehouseCancel(unreleased.getId(), ErrorDto.class);
        ResponseEntity<InternalOrderDto> cancelPicked = warehouseCancel(picked.getId(), InternalOrderDto.class);
        ResponseEntity<ErrorDto> cancelAgain = warehouseCancel(picked.getId(), ErrorDto.class);
        ResponseEntity<ErrorDto> cancelPacked = warehouseCancel(packed.getId(), ErrorDto.class);

        assertThat(cancelUnreleased.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cancelPicked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelPicked.getBody().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelPicked.getBody().fulfilmentState()).isEqualTo(FulfilmentState.CANCELLED);
        assertThat(cancelAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cancelPacked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stockOf(product)).isEqualTo(10 - 1 - 3);
    }

    @Test
    void productsAreListedAllOrById() {
        ProductEntity first = product("10.00", 3);
        ProductEntity second = product("20.00", 0);

        List<InternalProductDto> all = products("");
        List<InternalProductDto> byId = products("?ids=" + second.getId() + ",999999");

        assertThat(all).extracting(InternalProductDto::id).containsExactly(first.getId(), second.getId());
        assertThat(byId).singleElement().satisfies(product -> {
            assertThat(product.name()).isEqualTo(second.getName());
            assertThat(product.category()).isEqualTo("Tests");
            assertThat(product.price()).isEqualByComparingTo("20.00");
            assertThat(product.stockQuantity()).isZero();
        });
    }

    @Test
    void receiptsAddStockAndReplayIdempotentlyPerRequestId() {
        ProductEntity first = product("10.00", 0);
        ProductEntity second = product("20.00", 5);
        UUID requestId = UUID.randomUUID();
        InventoryReceiptDto receipt = new InventoryReceiptDto(requestId, InventoryMovementType.PURCHASE_RECEIVED, null,
                "PO-7 receipt", List.of(line(second, 4), line(first, 6)));

        ResponseEntity<InventoryReceiptResultDto> created = receive(receipt, InventoryReceiptResultDto.class);
        ResponseEntity<InventoryReceiptResultDto> replayed = receive(receipt, InventoryReceiptResultDto.class);
        ResponseEntity<ErrorDto> differentLines = receive(new InventoryReceiptDto(requestId,
                InventoryMovementType.PURCHASE_RECEIVED, null, "PO-7 receipt", List.of(line(second, 5), line(first, 6))),
                ErrorDto.class);
        ResponseEntity<ErrorDto> differentType = receive(new InventoryReceiptDto(requestId,
                InventoryMovementType.RETURN_RESTOCKED, null, "PO-7 receipt", receipt.lines()), ErrorDto.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody().movementIds()).hasSize(2).doesNotContainNull();
        assertThat(replayed.getBody().movementIds()).isEqualTo(created.getBody().movementIds());
        assertThat(differentLines.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(differentLines.getBody().getMessage()).isEqualTo("requestId already used with different lines");
        assertThat(differentType.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stockOf(first)).isEqualTo(6);
        assertThat(stockOf(second)).isEqualTo(9);
        var movement = movementRepository.findById(created.getBody().movementIds().getFirst()).orElseThrow();
        assertThat(movement.getProduct().getId()).isEqualTo(second.getId());
        assertThat(movement.getRequestId()).isEqualTo(requestId);
        assertThat(movement.getType()).isEqualTo(InventoryMovementType.PURCHASE_RECEIVED);
        assertThat(movement.getReason()).isEqualTo("PO-7 receipt");
        assertThat(movement.getActor()).isEqualTo("warehouse-service");
    }

    @Test
    void returnRestockLinksTheOrderAndInvalidReceiptsAreRejected() {
        ProductEntity product = product("10.00", 5);
        OrderDto order = order(clientToken, product, 2);

        ResponseEntity<InventoryReceiptResultDto> restocked = receive(new InventoryReceiptDto(UUID.randomUUID(),
                InventoryMovementType.RETURN_RESTOCKED, order.getId(), "RMA-1", List.of(line(product, 1))),
                InventoryReceiptResultDto.class);
        ResponseEntity<ErrorDto> wrongType = receive(new InventoryReceiptDto(UUID.randomUUID(),
                InventoryMovementType.ADMIN_ADJUSTMENT, null, "x", List.of(line(product, 1))), ErrorDto.class);
        ResponseEntity<ErrorDto> duplicateProduct = receive(new InventoryReceiptDto(UUID.randomUUID(),
                InventoryMovementType.PURCHASE_RECEIVED, null, "x", List.of(line(product, 1), line(product, 2))), ErrorDto.class);
        ResponseEntity<String> zeroQuantity = receive(new InventoryReceiptDto(UUID.randomUUID(),
                InventoryMovementType.PURCHASE_RECEIVED, null, "x", List.of(line(product, 0))), String.class);
        ResponseEntity<ErrorDto> unknownProduct = receive(new InventoryReceiptDto(UUID.randomUUID(),
                InventoryMovementType.PURCHASE_RECEIVED, null, "x",
                List.of(new InventoryReceiptLineDto(999999L, 1))), ErrorDto.class);
        ResponseEntity<ErrorDto> unknownOrder = receive(new InventoryReceiptDto(UUID.randomUUID(),
                InventoryMovementType.RETURN_RESTOCKED, 999999L, "x", List.of(line(product, 1))), ErrorDto.class);

        assertThat(restocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        var movement = movementRepository.findById(restocked.getBody().movementIds().getFirst()).orElseThrow();
        assertThat(movement.getType()).isEqualTo(InventoryMovementType.RETURN_RESTOCKED);
        assertThat(movement.getOrder().getId()).isEqualTo(order.getId());
        assertThat(wrongType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(duplicateProduct.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(zeroQuantity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknownProduct.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknownOrder.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stockOf(product)).isEqualTo(5 - 2 + 1);
    }

    @Test
    void notificationsAreRenderedByTheBackendAndQueuedOncePerRequestId() {
        UUID requestId = UUID.randomUUID();
        Map<String, String> properties = Map.of("orderId", "1", "carrier", "DPD", "trackingNumber", "12345678901234");
        InternalNotificationDto shipped = new InternalNotificationDto(requestId, client.getUsername(),
                EmailTemplate.ORDER_SHIPPED, properties);

        ResponseEntity<Void> accepted = notification(shipped, Void.class);
        ResponseEntity<Void> replayed = notification(shipped, Void.class);
        ResponseEntity<ErrorDto> unknownUser = notification(new InternalNotificationDto(UUID.randomUUID(),
                "nobody-" + UUID.randomUUID(), EmailTemplate.ORDER_SHIPPED, properties), ErrorDto.class);
        ResponseEntity<ErrorDto> foreignTemplate = notification(new InternalNotificationDto(UUID.randomUUID(),
                client.getUsername(), EmailTemplate.PASSWORD_RESET_REQUESTED, null), ErrorDto.class);
        ResponseEntity<ErrorDto> missingProperty = notification(new InternalNotificationDto(UUID.randomUUID(),
                client.getUsername(), EmailTemplate.RETURN_UPDATED, Map.of("orderId", "1")), ErrorDto.class);
        ResponseEntity<String> missingRequestId = notification(new InternalNotificationDto(null,
                client.getUsername(), EmailTemplate.ORDER_DELIVERED, Map.of("orderId", "1")), String.class);

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(emailEventRepository.findAll())
                .filteredOn(event -> requestId.equals(event.getRequestId()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getType()).isEqualTo(EmailTemplate.ORDER_SHIPPED);
                    assertThat(event.getRecipientEmail()).isEqualTo(client.getEmail());
                    assertThat(event.getPayload())
                            .contains("Order #1 has shipped")
                            .contains("is on its way with DPD. Tracking number: 12345678901234.");
                });
        assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(foreignTemplate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingProperty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingProperty.getBody().getMessage()).isEqualTo("properties.returnId is required for RETURN_UPDATED");
        assertThat(missingRequestId.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void carrierTrackingNotificationsAreQueued() {
        UUID outRequest = UUID.randomUUID();
        UUID failedRequest = UUID.randomUUID();
        Map<String, String> tracking = Map.of("orderId", "5", "carrier", "InPost", "trackingNumber", "620000000000000000000001");

        ResponseEntity<Void> out = notification(new InternalNotificationDto(outRequest, client.getUsername(),
                EmailTemplate.SHIPMENT_OUT_FOR_DELIVERY, tracking), Void.class);
        ResponseEntity<Void> failed = notification(new InternalNotificationDto(failedRequest, client.getUsername(),
                EmailTemplate.SHIPMENT_DELIVERY_FAILED, tracking), Void.class);
        ResponseEntity<ErrorDto> missingCarrier = notification(new InternalNotificationDto(UUID.randomUUID(),
                client.getUsername(), EmailTemplate.SHIPMENT_DELIVERY_FAILED, Map.of("orderId", "5", "trackingNumber", "1")),
                ErrorDto.class);
        ResponseEntity<ErrorDto> paymentTemplate = notification(new InternalNotificationDto(UUID.randomUUID(),
                client.getUsername(), EmailTemplate.PAYMENT_RECEIVED, Map.of("orderId", "5", "amount", "1.00")),
                ErrorDto.class);

        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(emailEventRepository.findAll())
                .filteredOn(event -> outRequest.equals(event.getRequestId()) || failedRequest.equals(event.getRequestId()))
                .extracting(event -> event.getType())
                .containsExactlyInAnyOrder(EmailTemplate.SHIPMENT_OUT_FOR_DELIVERY, EmailTemplate.SHIPMENT_DELIVERY_FAILED);
        assertThat(missingCarrier.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingCarrier.getBody().getMessage()).isEqualTo("properties.carrier is required for SHIPMENT_DELIVERY_FAILED");
        assertThat(paymentTemplate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void refundsAreBookedOnDeliveredOrdersUpToTheTotalAndReplayIdempotently() {
        OrderDto order = order(clientToken, product("40.00", 10), 2);
        OrderDto undelivered = order(clientToken, product("10.00", 10), 1);
        deliver(order.getId());
        UUID firstRequest = UUID.randomUUID();

        ResponseEntity<InternalOrderDto> first = refund(order.getId(), new OrderRefundDto(firstRequest, new BigDecimal("30.00"), "Return #1"), InternalOrderDto.class);
        ResponseEntity<InternalOrderDto> replay = refund(order.getId(), new OrderRefundDto(firstRequest, new BigDecimal("30.00"), "Return #1"), InternalOrderDto.class);
        ResponseEntity<ErrorDto> conflictingReplay = refund(order.getId(), new OrderRefundDto(firstRequest, new BigDecimal("31.00"), "Return #1"), ErrorDto.class);
        ResponseEntity<ErrorDto> tooMuch = refund(order.getId(), new OrderRefundDto(UUID.randomUUID(), new BigDecimal("50.01"), "Return #2"), ErrorDto.class);
        ResponseEntity<InternalOrderDto> rest = refund(order.getId(), new OrderRefundDto(UUID.randomUUID(), new BigDecimal("50.00"), "Return #2"), InternalOrderDto.class);
        ResponseEntity<ErrorDto> notDelivered = refund(undelivered.getId(), new OrderRefundDto(UUID.randomUUID(), BigDecimal.ONE, "Return #3"), ErrorDto.class);
        ResponseEntity<ErrorDto> unknownOrder = refund(999999L, new OrderRefundDto(UUID.randomUUID(), BigDecimal.ONE, "Return #4"), ErrorDto.class);
        ResponseEntity<String> zero = refund(order.getId(), new OrderRefundDto(UUID.randomUUID(), BigDecimal.ZERO, "Return #5"), String.class);
        OrderDto customerView = executeGet(ORDERS_ENDPOINT + "/" + order.getId(), getHeadersWith(clientToken), OrderDto.class).getBody();
        OrderDto untouchedView = executeGet(ORDERS_ENDPOINT + "/" + undelivered.getId(), getHeadersWith(clientToken), OrderDto.class).getBody();

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().refundedAmount()).isEqualByComparingTo("30.00");
        assertThat(replay.getBody().refundedAmount()).isEqualByComparingTo("30.00");
        assertThat(conflictingReplay.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tooMuch.getBody().getMessage()).isEqualTo("Refund of 50.01 exceeds the refundable amount of 50.00");
        assertThat(rest.getBody().refundedAmount()).isEqualByComparingTo("80.00");
        assertThat(notDelivered.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(unknownOrder.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(zero.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(customerView.getRefundedAmount()).isEqualByComparingTo("80.00");
        assertThat(customerView.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(untouchedView.getRefundedAmount()).isNull();
    }

    @Test
    void ordersCanBeReadInBatchesById() {
        ProductEntity product = product("5.00", 10);
        OrderDto first = order(clientToken, product, 1);
        OrderDto second = order(clientToken, product, 1);

        ResponseEntity<List<InternalOrderDto>> response = executeGet(
                INTERNAL + "/orders?ids=" + second.getId() + "," + first.getId() + ",999999", serviceHeaders(TOKEN),
                new ParameterizedTypeReference<List<InternalOrderDto>>() {});
        ResponseEntity<String> withoutToken = executeGet(INTERNAL + "/orders?ids=" + first.getId(), getJsonOnlyHeaders(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(InternalOrderDto::id).containsExactly(first.getId(), second.getId());
        assertThat(response.getBody().getFirst().items()).hasSize(1);
        assertThat(withoutToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void jwksPublishesTheKeyThatSignsAccessTokens() throws Exception {
        ResponseEntity<String> response = executeGet(INTERNAL + "/jwks.json", serviceHeaders(TOKEN), String.class);
        ResponseEntity<String> withoutToken = executeGet(INTERNAL + "/jwks.json", getJsonOnlyHeaders(), String.class);
        var jwk = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody()).get("keys").get(0);
        var header = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                java.util.Base64.getUrlDecoder().decode(clientToken.split("\\.")[0]));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jwk.get("kty").asText()).isEqualTo("RSA");
        assertThat(jwk.get("kid").asText()).isEqualTo(header.get("kid").asText());
        assertThat(jwk.has("d")).isFalse();
        assertThat(header.get("alg").asText()).isEqualTo("RS256");
        assertThat(withoutToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private <T> ResponseEntity<T> refund(Long orderId, OrderRefundDto request, Class<T> type) {
        return executePost(INTERNAL + "/orders/" + orderId + "/refunds", request, serviceHeaders(TOKEN), type);
    }

    private HttpHeaders serviceHeaders(String token) {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Service-Token", token);
        return headers;
    }

    private PageDto<InternalOrderDto> queue(String query) {
        ResponseEntity<PageDto<InternalOrderDto>> response = executeGet(INTERNAL + "/orders" + query, serviceHeaders(TOKEN),
                new ParameterizedTypeReference<PageDto<InternalOrderDto>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<InternalProductDto> products(String query) {
        ResponseEntity<List<InternalProductDto>> response = executeGet(INTERNAL + "/products" + query, serviceHeaders(TOKEN),
                new ParameterizedTypeReference<List<InternalProductDto>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private <T> ResponseEntity<T> fulfilment(Long orderId, FulfilmentState state, Class<T> type) {
        return executePut(INTERNAL + "/orders/" + orderId + "/fulfilment", new FulfilmentUpdateDto(state),
                serviceHeaders(TOKEN), type);
    }

    private <T> ResponseEntity<T> warehouseCancel(Long orderId, Class<T> type) {
        return executePost(INTERNAL + "/orders/" + orderId + "/warehouse-cancel", null, serviceHeaders(TOKEN), type);
    }

    private <T> ResponseEntity<T> cancel(Long orderId, String token, Class<T> type) {
        return execute(HttpMethod.POST, ORDERS_ENDPOINT + "/" + orderId + "/cancel", null, getHeadersWith(token), type);
    }

    private <T> ResponseEntity<T> receive(InventoryReceiptDto receipt, Class<T> type) {
        return executePost(INTERNAL + "/inventory/receipts", receipt, serviceHeaders(TOKEN), type);
    }

    private <T> ResponseEntity<T> notification(InternalNotificationDto notification, Class<T> type) {
        return executePost(INTERNAL + "/notifications", notification, serviceHeaders(TOKEN), type);
    }

    private static InventoryReceiptLineDto line(ProductEntity product, int quantity) {
        return new InventoryReceiptLineDto(product.getId(), quantity);
    }
}
