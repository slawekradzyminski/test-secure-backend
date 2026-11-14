package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.inventory.InventoryItemDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.dto.product.ProductUpdateDto;
import com.awesome.testing.dto.stock.StockLevelDto;
import com.awesome.testing.dto.stock.StockLevelsDto;
import com.awesome.testing.dto.stock.StockLevelsResultDto;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryMovementEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.awesome.testing.entity.stock.StockCommandEntity;
import com.awesome.testing.internal.dto.InventoryExportDto;
import com.awesome.testing.internal.dto.OrderReferenceDto;
import com.awesome.testing.repository.inventory.InventoryMovementRepository;
import com.awesome.testing.repository.stock.StockCommandRepository;
import com.awesome.testing.service.stock.StockCommandDispatcher;
import com.awesome.testing.stock.FakeStockLedger;
import com.awesome.testing.stock.FakeStockLedger.Injected;
import com.awesome.testing.stock.FakeStockLedger.Operation;
import com.awesome.testing.stock.FakeWarehouse;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * warehouse-service owns the stock ledger (backend 3.22.0): the checkout saga, the 503 when
 * warehouse-service fails, the level push into the projection, the stock command outbox and the
 * internal endpoints warehouse-service calls. The fake ledger ({@link FakeWarehouse}) answers over
 * real HTTP; faults are injected per product.
 */
class InventoryOwnershipFlowTest extends AbstractCommerceFlowTest {

    private static final String INTERNAL = "/internal/v1";
    private static final String WAREHOUSE_TOKEN = "test-warehouse-token";
    private static final String UNAVAILABLE = "Inventory service unavailable";

    @Autowired
    private StockCommandDispatcher dispatcher;

    @Autowired
    private StockCommandRepository commands;

    @Autowired
    private InventoryMovementRepository movementRepository;

    private final List<Long> faulty = new ArrayList<>();

    @AfterEach
    void heal() {
        faulty.forEach(FakeWarehouse.ledger()::heal);
    }

    @Test
    void checkoutReservesInTheLedgerStoresTheReservationAndConfirmsItWithTheOrderId() {
        ProductEntity product = product("10.00", 5);

        OrderDto order = order(clientToken, product, 2);

        assertThat(stockOf(product)).isEqualTo(3);
        OrderEntity stored = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(stored.getStockReservationId()).isNotNull();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockLedgerVersion()).isPositive();
        assertThat(FakeWarehouse.ledger().movements(product.getId())).singleElement().satisfies(movement -> {
            assertThat(movement.type()).isEqualTo("ORDER_DEDUCTED");
            assertThat(movement.delta()).isEqualTo(-2);
            assertThat(movement.actor()).isEqualTo(client.getUsername());
            assertThat(movement.reason()).isEqualTo("Order deduction");
            assertThat(movement.orderId()).as("set by the confirm").isEqualTo(order.getId());
        });
        List<LoggedRequest> reserve = FakeWarehouse.requests(WireMock.postRequestedFor(
                WireMock.urlEqualTo("/internal/v1/stock/reservations"))
                .withRequestBody(WireMock.containing("\"reservationId\":\"" + stored.getStockReservationId() + "\"")));
        assertThat(reserve).singleElement().satisfies(request -> {
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + FakeStockLedger.TOKEN);
            assertThat(request.getBodyAsString()).contains("\"username\":\"" + client.getUsername() + "\"",
                    "\"lines\":[{\"productId\":" + product.getId() + ",\"quantity\":2}]");
        });
        assertThat(FakeWarehouse.requests(WireMock.postRequestedFor(WireMock.urlEqualTo(
                "/internal/v1/stock/reservations/" + stored.getStockReservationId() + "/confirm"))
                .withRequestBody(WireMock.equalToJson("{\"orderId\":" + order.getId() + "}")))).hasSize(1);
        // Course contract: the order body has no reservation id.
        String body = executeGet(ORDERS_ENDPOINT + "/" + order.getId(), getHeadersWith(clientToken), String.class).getBody();
        assertThat(body).doesNotContain(stored.getStockReservationId().toString()).doesNotContain("stockReservation");
    }

    @Test
    void duplicateCartLinesAreMergedIntoOneReservationLinePerProduct() {
        ProductEntity first = product("10.00", 9);
        ProductEntity second = product("5.00", 9);
        addToCart(clientToken, second, 1);
        addToCart(clientToken, first, 2);

        OrderDto order = checkout(clientToken);

        UUID reservation = orderRepository.findById(order.getId()).orElseThrow().getStockReservationId();
        assertThat(FakeWarehouse.requests(WireMock.postRequestedFor(WireMock.urlEqualTo("/internal/v1/stock/reservations"))
                .withRequestBody(WireMock.containing(reservation.toString())))).singleElement()
                .satisfies(request -> assertThat(request.getBodyAsString()).contains("\"lines\":[{\"productId\":"
                        + first.getId() + ",\"quantity\":2},{\"productId\":" + second.getId() + ",\"quantity\":1}]"));
        assertThat(stockOf(first)).isEqualTo(7);
        assertThat(stockOf(second)).isEqualTo(8);
    }

    @Test
    void theCallersRequestIdAndTraceReachWarehouseService() {
        ProductEntity product = product("10.00", 5);
        addToCart(clientToken, product, 1);
        HttpHeaders headers = getHeadersWith(clientToken);
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        headers.set("X-Request-Id", "stock-trace-" + product.getId());
        headers.set("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01");

        ResponseEntity<OrderDto> order = executePost(ORDERS_ENDPOINT, getRandomAddress(), headers, OrderDto.class);

        assertThat(order.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(FakeWarehouse.requests(WireMock.postRequestedFor(WireMock.urlPathMatching("/internal/v1/stock/.*"))
                .withHeader("X-Request-Id", WireMock.equalTo("stock-trace-" + product.getId()))))
                .as("reserve and confirm").hasSize(2)
                .allSatisfy(request -> assertThat(request.getHeader("traceparent"))
                        .matches("00-" + traceId + "-[0-9a-f]{16}-01"));
    }

    @Test
    void anInsufficientLedgerAnswersTheSame409AsBeforeAndKeepsTheCart() {
        ProductEntity product = product("10.00", 5);
        addToCart(clientToken, product, 4);
        FakeWarehouse.ledger().set(product.getId(), 3);
        assertLedgerAheadOfTheProjection(product);

        ResponseEntity<ErrorDto> response = executePost(ORDERS_ENDPOINT, getRandomAddress(),
                getHeadersWith(clientToken), ErrorDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("Insufficient stock for product " + product.getId());
        assertThat(cart(clientToken).getTotalItems()).isEqualTo(4);
        assertThat(orderRepository.countByUsername(client.getUsername())).isZero();
    }

    @Test
    void checkoutAnswers503AndKeepsTheCartWhenWarehouseServiceFails() {
        for (Injected fault : List.of(
                Injected.status(500, Operation.RESERVE),
                Injected.status(503, Operation.RESERVE),
                Injected.timeout(Operation.RESERVE),
                Injected.reset(Operation.RESERVE))) {
            ProductEntity product = faulty(product("10.00", 5), fault);
            addToCart(clientToken, product, 1);

            ResponseEntity<ErrorDto> response = executePost(ORDERS_ENDPOINT, getRandomAddress(),
                    getHeadersWith(clientToken), ErrorDto.class);

            assertThat(response.getStatusCode()).as(fault.toString()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().getMessage()).isEqualTo(UNAVAILABLE);
            assertThat(stockOf(product)).isEqualTo(5);
            assertThat(cart(clientToken).getTotalItems()).isEqualTo(1);
            executeDelete(CART_ITEMS_ENDPOINT + "/" + product.getId(), getHeadersWith(clientToken), String.class);
        }
        assertThat(orderRepository.countByUsername(client.getUsername())).isZero();
    }

    @Test
    void aFailedConfirmStillCreatesTheOrderForTheReaperToConfirm() {
        ProductEntity product = faulty(product("10.00", 5), Injected.status(503, Operation.CONFIRM));

        OrderDto order = order(clientToken, product, 2);

        assertThat(order.getStatus().name()).isEqualTo("PENDING");
        assertThat(stockOf(product)).isEqualTo(3);
        UUID reservation = orderRepository.findById(order.getId()).orElseThrow().getStockReservationId();
        assertThat(byReservation(reservation.toString(), OrderReferenceDto.class).getBody())
                .isEqualTo(new OrderReferenceDto(order.getId()));
    }

    @Test
    void cartAndReadsKeepWorkingWhileWarehouseServiceIsDown() {
        ProductEntity product = faulty(product("10.00", 5), Injected.status(503, Operation.RESERVE,
                Operation.values()));

        addToCart(clientToken, product, 2);
        ResponseEntity<InventoryItemDto> item = executeGet("/api/v1/admin/inventory/" + product.getId(),
                getHeadersWith(adminToken), InventoryItemDto.class);
        ResponseEntity<ProductDto> read = executeGet(PRODUCTS_ENDPOINT + "/" + product.getId(),
                getHeadersWith(clientToken), ProductDto.class);
        ResponseEntity<ErrorDto> movements = executeGet("/api/v1/admin/inventory/" + product.getId() + "/movements",
                getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<ErrorDto> adjust = executePost("/api/v1/admin/inventory/" + product.getId() + "/adjustments",
                InventoryAdjustmentDto.builder().delta(1).reason("x").requestId(UUID.randomUUID()).build(),
                getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<ErrorDto> stockUpdate = executePut(PRODUCTS_ENDPOINT + "/" + product.getId(),
                ProductUpdateDto.builder().name("Renamed while down").stockQuantity(9).build(),
                getHeadersWith(adminToken), ErrorDto.class);

        assertThat(cart(clientToken).getTotalItems()).isEqualTo(2);
        assertThat(item.getBody().getAvailableQuantity()).isEqualTo(5);
        assertThat(read.getBody().getStockQuantity()).isEqualTo(5);
        assertThat(List.of(movements, adjust, stockUpdate)).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().getMessage()).isEqualTo(UNAVAILABLE);
        });
        assertThat(productRepository.findById(product.getId()).orElseThrow().getName())
                .as("a rejected stock change leaves the product unchanged").isNotEqualTo("Renamed while down");
    }

    @Test
    void graphqlAnswersUnavailableWhenWarehouseServiceFails() {
        ProductEntity product = faulty(product("10.00", 5), Injected.status(502, Operation.ADJUST, Operation.MOVEMENTS));
        String mutation = "mutation($id:ID!){adjustInventory(productId:$id,input:{delta:1,reason:\"x\",requestId:\""
                + UUID.randomUUID() + "\"}){id}}";

        ResponseEntity<String> adjust = executePost("/api/v1/graphql",
                java.util.Map.of("query", mutation, "variables", java.util.Map.of("id", product.getId())),
                getHeadersWith(adminToken), String.class);
        ResponseEntity<String> movements = executePost("/api/v1/graphql",
                java.util.Map.of("query", "query($id:ID!){inventoryMovements(productId:$id){total}}",
                        "variables", java.util.Map.of("id", product.getId())),
                getHeadersWith(adminToken), String.class);

        assertThat(List.of(adjust, movements)).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"message\":\"" + UNAVAILABLE + "\"", "\"code\":\"UNAVAILABLE\"");
        });
        assertThat(stockOf(product)).isEqualTo(5);
    }

    @Test
    void cancellingHandsTheRestoreToWarehouseServiceAndTheLevelPushCatchesTheProjectionUp() {
        ProductEntity product = product("10.00", 5);
        OrderDto order = order(clientToken, product, 2);

        ResponseEntity<OrderDto> cancelled = executePost(ORDERS_ENDPOINT + "/" + order.getId() + "/cancel", null,
                getHeadersWith(clientToken), OrderDto.class);

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stockOf(product)).as("restored asynchronously").isEqualTo(3);
        UUID reservation = orderRepository.findById(order.getId()).orElseThrow().getStockReservationId();
        assertThat(FakeWarehouse.requests(WireMock.postRequestedFor(WireMock.urlEqualTo(
                "/internal/v1/stock/reservations/" + reservation + "/release"))))
                .as("a confirmed reservation is restored through order.cancelled, never released").isEmpty();
        assertThat(warehouseSimulator.restoreCancelledStock(order.getId())).isOne();
        assertThat(stockOf(product)).isEqualTo(5);
        assertThat(FakeWarehouse.ledger().movements(product.getId()).getFirst()).satisfies(movement -> {
            assertThat(movement.type()).isEqualTo("ORDER_RESTORED");
            assertThat(movement.orderId()).isEqualTo(order.getId());
            assertThat(movement.reason()).isEqualTo("Order cancellation");
            assertThat(movement.actor()).isEqualTo(client.getUsername());
        });
    }

    @Test
    void levelsApplyOnlyNewerVersionsAndIgnoreUnknownProducts() {
        ProductEntity product = product("10.00", 0);
        ProductEntity other = product("10.00", 4);

        ResponseEntity<StockLevelsResultDto> first = levels(List.of(
                new StockLevelDto(product.getId(), 6, 3L),
                new StockLevelDto(Long.MAX_VALUE, 1, 1L),
                new StockLevelDto(other.getId(), 1, 1L)), StockLevelsResultDto.class);
        ResponseEntity<StockLevelsResultDto> stale = levels(List.of(
                new StockLevelDto(product.getId(), 1, 3L),
                new StockLevelDto(product.getId(), 2, 2L)), StockLevelsResultDto.class);
        ResponseEntity<StockLevelsResultDto> newer = levels(List.of(
                new StockLevelDto(product.getId(), 0, 4L)), StockLevelsResultDto.class);
        ResponseEntity<StockLevelsResultDto> empty = levels(List.of(), StockLevelsResultDto.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isEqualTo(new StockLevelsResultDto(2, 1));
        assertThat(stale.getBody()).isEqualTo(new StockLevelsResultDto(0, 2));
        assertThat(newer.getBody()).isEqualTo(new StockLevelsResultDto(1, 0));
        assertThat(empty.getBody()).isEqualTo(new StockLevelsResultDto(0, 0));
        ProductEntity stored = productRepository.findById(product.getId()).orElseThrow();
        assertThat(stored.getStockQuantity()).isZero();
        assertThat(stored.getStockLedgerVersion()).isEqualTo(4L);
        assertThat(stored.getUpdatedAt()).isAfter(product.getUpdatedAt());
        assertThat(stockOf(other)).isEqualTo(1);
    }

    @Test
    void levelsRejectInvalidBodiesAndCallersOtherThanWarehouseService() {
        ProductEntity product = product("10.00", 2);
        String valid = "{\"levels\":[{\"productId\":" + product.getId() + ",\"quantity\":1,\"ledgerVersion\":1}]}";

        List<ResponseEntity<String>> invalid = List.of(
                rawLevels("{}", WAREHOUSE_TOKEN),
                rawLevels("{\"levels\":null}", WAREHOUSE_TOKEN),
                rawLevels("{\"levels\":[null]}", WAREHOUSE_TOKEN),
                rawLevels("{\"levels\":[{\"productId\":1,\"quantity\":-1,\"ledgerVersion\":1}]}", WAREHOUSE_TOKEN),
                rawLevels("{\"levels\":[{\"productId\":1,\"quantity\":1,\"ledgerVersion\":0}]}", WAREHOUSE_TOKEN),
                rawLevels("{\"levels\":[{\"productId\":0,\"quantity\":1,\"ledgerVersion\":1}]}", WAREHOUSE_TOKEN),
                rawLevels("{\"levels\":[{\"quantity\":1,\"ledgerVersion\":1}]}", WAREHOUSE_TOKEN),
                rawLevels("not json", WAREHOUSE_TOKEN));
        ResponseEntity<String> tooMany = levels(java.util.stream.LongStream.rangeClosed(1, StockLevelsDto.MAX_LEVELS + 1)
                .mapToObj(id -> new StockLevelDto(id, 1, 1L)).toList(), String.class);
        ResponseEntity<String> wrongToken = rawLevels(valid, "wrong-token");
        ResponseEntity<String> paymentToken = rawLevels(valid, "test-payment-token");
        ResponseEntity<String> notificationToken = rawLevels(valid, "test-notification-token");

        assertThat(invalid).allSatisfy(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(List.of(wrongToken, paymentToken, notificationToken))
                .allSatisfy(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(stockOf(product)).isEqualTo(2);
    }

    @Test
    void productCreateAndDeleteReachWarehouseServiceThroughTheOutbox() {
        ProductCreateDto create = ProductCreateDto.builder().name("Outbox product " + System.nanoTime())
                .description("Created through the API").price(new BigDecimal("3.50")).stockQuantity(6)
                .category("Tests").build();
        ResponseEntity<ProductDto> created = executePost(PRODUCTS_ENDPOINT, create, getHeadersWith(adminToken),
                ProductDto.class);
        Long id = created.getBody().getId();
        StockCommandEntity pending = commands.findAll().stream()
                .filter(command -> command.getProductId().equals(id)).findFirst().orElseThrow();

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(pending.getCommand()).isEqualTo(StockCommandEntity.Command.INITIAL_STOCK);
        assertThat(pending.getStatus()).isEqualTo(StockCommandEntity.Status.PENDING);
        assertThat(pending.getInitialQuantity()).isEqualTo(6);
        assertThat(pending.getActor()).isEqualTo("admin");
        assertThat(productRepository.findById(id).orElseThrow().getStockLedgerVersion()).isZero();

        assertThat(dispatcher.dispatchDue()).isPositive();

        assertThat(commands.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(StockCommandEntity.Status.DONE);
        assertThat(FakeWarehouse.ledger().movements(id)).singleElement().satisfies(movement -> {
            assertThat(movement.type()).isEqualTo("INITIAL_STOCK");
            assertThat(movement.delta()).isEqualTo(6);
            assertThat(movement.reason()).isEqualTo("Initial stock");
        });
        assertThat(productRepository.findById(id).orElseThrow().getStockLedgerVersion()).isEqualTo(1L);
        assertThat(FakeWarehouse.requests(WireMock.putRequestedFor(WireMock.urlEqualTo("/internal/v1/stock/items/" + id))
                .withRequestBody(WireMock.equalToJson("{\"initialQuantity\":6,\"actor\":\"admin\",\"requestId\":\""
                        + pending.getRequestId() + "\"}")))).hasSize(1);

        executeDelete(PRODUCTS_ENDPOINT + "/" + id, getHeadersWith(adminToken), String.class);
        dispatcher.dispatchDue();

        assertThat(commands.findAll()).filteredOn(command -> command.getProductId().equals(id)
                        && command.getCommand() == StockCommandEntity.Command.DELETE)
                .singleElement().extracting(StockCommandEntity::getStatus).isEqualTo(StockCommandEntity.Status.DONE);
        assertThat(FakeWarehouse.ledger().movements(id)).isEmpty();
        assertThat(FakeWarehouse.requests(WireMock.deleteRequestedFor(
                WireMock.urlEqualTo("/internal/v1/stock/items/" + id)))).hasSize(1);
    }

    @Test
    void aFailedStockCommandStaysPendingAndIsRetriedOnceWarehouseServiceRecovers() {
        ResponseEntity<ProductDto> created = executePost(PRODUCTS_ENDPOINT, ProductCreateDto.builder()
                .name("Retried product " + System.nanoTime()).description("Created while warehouse is down")
                .price(new BigDecimal("3.50")).stockQuantity(2).category("Tests").build(),
                getHeadersWith(adminToken), ProductDto.class);
        Long id = created.getBody().getId();
        FakeWarehouse.ledger().fail(id, Injected.status(503, Operation.ITEMS));
        faulty.add(id);

        dispatcher.dispatchDue();
        StockCommandEntity failed = commands.findAll().stream()
                .filter(command -> command.getProductId().equals(id)).findFirst().orElseThrow();
        assertThat(created.getStatusCode()).as("the response does not wait for warehouse-service")
                .isEqualTo(HttpStatus.CREATED);
        assertThat(failed.getStatus()).isEqualTo(StockCommandEntity.Status.PENDING);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).isEqualTo(UNAVAILABLE);
        assertThat(failed.getNextAttemptAt()).isAfter(failed.getCreatedAt());

        FakeWarehouse.ledger().heal(id);
        failed.setNextAttemptAt(failed.getCreatedAt());
        commands.save(failed);
        dispatcher.dispatchDue();

        StockCommandEntity done = commands.findById(failed.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(StockCommandEntity.Status.DONE);
        assertThat(done.getAttempts()).isEqualTo(2);
        assertThat(done.getLastError()).isNull();
        assertThat(done.getProcessedAt()).isNotNull();
    }

    @Test
    void anInitialStockCommandOfADeletedProductCompletesWithoutACall() {
        ResponseEntity<ProductDto> created = executePost(PRODUCTS_ENDPOINT, ProductCreateDto.builder()
                .name("Short-lived product " + System.nanoTime()).description("Deleted before dispatch")
                .price(new BigDecimal("3.50")).stockQuantity(2).category("Tests").build(),
                getHeadersWith(adminToken), ProductDto.class);
        Long id = created.getBody().getId();
        executeDelete(PRODUCTS_ENDPOINT + "/" + id, getHeadersWith(adminToken), String.class);

        dispatcher.dispatchDue();

        assertThat(commands.findAll()).filteredOn(command -> command.getProductId().equals(id))
                .extracting(StockCommandEntity::getStatus).containsOnly(StockCommandEntity.Status.DONE);
        assertThat(FakeWarehouse.requests(WireMock.putRequestedFor(WireMock.urlEqualTo("/internal/v1/stock/items/" + id))))
                .isEmpty();
    }

    @Test
    void adminAdjustmentsAndMovementsGoThroughTheLedger() {
        ProductEntity product = product("10.00", 5);
        UUID requestId = UUID.randomUUID();
        InventoryAdjustmentDto adjustment = InventoryAdjustmentDto.builder().delta(-2).reason("Damaged")
                .requestId(requestId).build();

        ResponseEntity<InventoryMovementDto> adjusted = executePost(
                "/api/v1/admin/inventory/" + product.getId() + "/adjustments", adjustment, getHeadersWith(adminToken),
                InventoryMovementDto.class);
        ResponseEntity<String> raw = executePost(
                "/api/v1/admin/inventory/" + product.getId() + "/adjustments", adjustment, getHeadersWith(adminToken),
                String.class);
        ResponseEntity<PageDto<InventoryMovementDto>> movements = executeGet(
                "/api/v1/admin/inventory/" + product.getId() + "/movements?page=0&size=1", getHeadersWith(adminToken),
                new ParameterizedTypeReference<>() {});
        ResponseEntity<ErrorDto> unknown = executeGet("/api/v1/admin/inventory/" + Long.MAX_VALUE + "/movements",
                getHeadersWith(adminToken), ErrorDto.class);

        assertThat(adjusted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(adjusted.getBody().getQuantityAfter()).isEqualTo(3);
        assertThat(adjusted.getBody().getType()).isEqualTo(InventoryMovementType.ADMIN_ADJUSTMENT);
        assertThat(raw.getStatusCode()).as("replay").isEqualTo(HttpStatus.CREATED);
        assertThat(raw.getBody()).as("the public movement has no ledger version").doesNotContain("ledgerVersion")
                .contains("\"orderId\":null");
        assertThat(stockOf(product)).isEqualTo(3);
        assertThat(movements.getBody().getContent()).singleElement().satisfies(movement -> {
            assertThat(movement.getId()).isEqualTo(adjusted.getBody().getId());
            assertThat(movement.getRequestId()).isEqualTo(requestId);
        });
        assertThat(movements.getBody().getTotalElements()).isEqualTo(1);
        assertThat(movements.getBody().getPageSize()).isEqualTo(1);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void byReservationAnswersEveryStatus() {
        ProductEntity product = product("10.00", 5);
        OrderDto order = order(clientToken, product, 1);
        UUID reservation = orderRepository.findById(order.getId()).orElseThrow().getStockReservationId();

        ResponseEntity<OrderReferenceDto> found = byReservation(reservation.toString(), OrderReferenceDto.class);
        ResponseEntity<ErrorDto> unknown = byReservation(UUID.randomUUID().toString(), ErrorDto.class);
        ResponseEntity<ErrorDto> malformed = byReservation("not-a-uuid", ErrorDto.class);
        ResponseEntity<ErrorDto> wrongToken = executeGet(INTERNAL + "/orders/by-reservation/" + reservation,
                serviceHeaders("test-payment-token"), ErrorDto.class);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).isEqualTo(new OrderReferenceDto(order.getId()));
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().getMessage()).isEqualTo("Order not found");
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrongToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void exportPagesProductsWithTheirFrozenMovements() {
        ProductEntity first = product("10.00", 5);
        ProductEntity second = product("10.00", 0);
        InventoryMovementEntity movement = movementRepository.save(InventoryMovementEntity.builder()
                .product(first).type(InventoryMovementType.INITIAL_STOCK).delta(5).quantityAfter(5)
                .actor("admin").reason("Initial stock").build());

        ResponseEntity<String> raw = export("?afterProductId=" + (first.getId() - 1) + "&limit=1", String.class);
        ResponseEntity<InventoryExportDto> page = export("?afterProductId=" + (first.getId() - 1) + "&limit=1",
                InventoryExportDto.class);
        ResponseEntity<InventoryExportDto> next = export("?afterProductId=" + first.getId(), InventoryExportDto.class);
        ResponseEntity<ErrorDto> tooLarge = export("?limit=501", ErrorDto.class);
        ResponseEntity<ErrorDto> zero = export("?limit=0", ErrorDto.class);
        ResponseEntity<ErrorDto> malformed = export("?afterProductId=abc", ErrorDto.class);
        ResponseEntity<ErrorDto> wrongToken = executeGet(INTERNAL + "/inventory/export",
                serviceHeaders("test-notification-token"), ErrorDto.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody().nextAfterProductId()).isEqualTo(first.getId());
        assertThat(page.getBody().items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(first.getId());
            assertThat(item.quantity()).isEqualTo(5);
            assertThat(item.movements()).singleElement().satisfies(exported -> {
                assertThat(exported.id()).isEqualTo(movement.getId());
                assertThat(exported.type()).isEqualTo(InventoryMovementType.INITIAL_STOCK);
                assertThat(exported.orderId()).isNull();
            });
        });
        assertThat(raw.getBody()).contains("\"requestId\":null", "\"orderId\":null")
                .doesNotContain("\"productId\":" + first.getId() + ",\"orderId\"");
        assertThat(next.getBody().items()).extracting(item -> item.productId()).contains(second.getId());
        assertThat(next.getBody().nextAfterProductId()).isNull();
        assertThat(List.of(tooLarge, zero, malformed))
                .allSatisfy(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(wrongToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ProductEntity faulty(ProductEntity product, Injected fault) {
        FakeWarehouse.ledger().fail(product.getId(), fault);
        faulty.add(product.getId());
        return product;
    }

    /** The projection still shows the old quantity: warehouse-service has not pushed the change yet. */
    private void assertLedgerAheadOfTheProjection(ProductEntity product) {
        assertThat(FakeWarehouse.ledger().item(product.getId()).orElseThrow().quantity()).isEqualTo(3);
    }

    private CartDto cart(String token) {
        return executeGet(CART_ENDPOINT, getHeadersWith(token), CartDto.class).getBody();
    }

    private <T> ResponseEntity<T> levels(List<StockLevelDto> levels, Class<T> type) {
        return executePost(INTERNAL + "/inventory/levels", new StockLevelsDto(levels), serviceHeaders(WAREHOUSE_TOKEN),
                type);
    }

    private ResponseEntity<String> rawLevels(String body, String token) {
        return executePost(INTERNAL + "/inventory/levels", body, serviceHeaders(token), String.class);
    }

    private <T> ResponseEntity<T> byReservation(String reservationId, Class<T> type) {
        return executeGet(INTERNAL + "/orders/by-reservation/" + reservationId, serviceHeaders(WAREHOUSE_TOKEN), type);
    }

    private <T> ResponseEntity<T> export(String query, Class<T> type) {
        return executeGet(INTERNAL + "/inventory/export" + query, serviceHeaders(WAREHOUSE_TOKEN), type);
    }

    private HttpHeaders serviceHeaders(String token) {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Service-Token", token);
        return headers;
    }
}
