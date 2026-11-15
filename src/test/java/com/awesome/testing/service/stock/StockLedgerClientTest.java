package com.awesome.testing.service.stock;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.correlation.CorrelationIds;
import com.awesome.testing.correlation.CorrelationTracing;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.stock.AdjustStockRequestDto;
import com.awesome.testing.dto.stock.CreateStockItemRequestDto;
import com.awesome.testing.dto.stock.LedgerMovementDto;
import com.awesome.testing.dto.stock.ReservationDto;
import com.awesome.testing.dto.stock.ReservedLineDto;
import com.awesome.testing.dto.stock.StockLevelDto;
import com.awesome.testing.dto.stock.StockLineDto;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The warehouse-service stock ledger client against a WireMock warehouse. */
@Execution(ExecutionMode.SAME_THREAD)
class StockLedgerClientTest {

    private static final String TOKEN = "backend-secret";
    private static final UUID RESERVATION = UUID.fromString("6f1c2e0a-7b8d-4e9f-a0b1-c2d3e4f5a6b7");
    private static WireMockServer warehouse;

    private StockLedgerClient client;

    @BeforeAll
    static void start() {
        warehouse = new WireMockServer(options().dynamicPort());
        warehouse.start();
    }

    @AfterAll
    static void stop() {
        warehouse.stop();
    }

    @BeforeEach
    void setUp() {
        warehouse.resetAll();
        client = client(TOKEN, Duration.ofMillis(500));
    }

    private static StockLedgerClient client(String token, Duration readTimeout) {
        return new StockLedgerClient(warehouse.baseUrl(), Duration.ofSeconds(1), readTimeout, token,
                CorrelationTracing.disabled());
    }

    @Test
    void reserveSendsTheBearerTokenTheCallersIdsAndTheLinesAndParsesTheReply() {
        warehouse.stubFor(post("/internal/v1/stock/reservations").willReturn(json(201, """
                {"reservationId":"%s","lines":[{"productId":1,"quantityAfter":8,"ledgerVersion":42}],"extra":true}
                """.formatted(RESERVATION))));
        CorrelationIds ids = CorrelationIds.fromHeaders("checkout-7", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        ReservationDto reservation;
        try (CorrelationIds.Scope ignored = ids.open()) {
            reservation = client.reserve(RESERVATION, "alice", List.of(new StockLineDto(1L, 2)));
        }

        assertThat(reservation).isEqualTo(new ReservationDto(RESERVATION, List.of(new ReservedLineDto(1L, 8, 42L))));
        warehouse.verify(postRequestedFor(urlEqualTo("/internal/v1/stock/reservations"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withHeader("X-Request-Id", equalTo("checkout-7"))
                .withHeader("traceparent", matching("00-4bf92f3577b34da6a3ce929d0e0e4736-[0-9a-f]{16}-01"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        {"reservationId":"%s","username":"alice","lines":[{"productId":1,"quantity":2}]}
                        """.formatted(RESERVATION))));
    }

    @Test
    void aCallOutsideARequestGetsFreshIdsAndABlankTokenSendsNoAuthorization() {
        warehouse.stubFor(post(urlPathMatching(".*/release")).willReturn(json(200,
                "{\"reservationId\":\"" + RESERVATION + "\",\"status\":\"RELEASED\"}")));

        client("  ", Duration.ofMillis(500)).release(RESERVATION);

        warehouse.verify(postRequestedFor(urlEqualTo("/internal/v1/stock/reservations/" + RESERVATION + "/release"))
                .withHeader("Authorization", absent())
                .withHeader("X-Request-Id", matching("[0-9a-f-]{36}"))
                .withHeader("traceparent", matching("00-[0-9a-f]{32}-[0-9a-f]{16}-01")));
    }

    @Test
    void confirmCreateItemAdjustAndDeleteUseTheirPathsAndMethods() {
        warehouse.stubFor(post(urlPathMatching(".*/confirm")).willReturn(json(200,
                "{\"reservationId\":\"" + RESERVATION + "\",\"lines\":[]}")));
        warehouse.stubFor(put("/internal/v1/stock/items/5").willReturn(json(201,
                "{\"productId\":5,\"quantity\":10,\"ledgerVersion\":1}")));
        warehouse.stubFor(post("/internal/v1/stock/adjustments").willReturn(json(201, """
                {"id":7,"productId":5,"orderId":null,"type":"ADMIN_ADJUSTMENT","delta":-3,"quantityAfter":7,
                 "actor":"admin","reason":"Damaged","requestId":"%s","createdAt":"2026-11-14T10:00:00.123456",
                 "ledgerVersion":43}
                """.formatted(RESERVATION))));
        warehouse.stubFor(delete("/internal/v1/stock/items/5").willReturn(aResponse().withStatus(204)));

        ReservationDto confirmed = client.confirm(RESERVATION, 123L);
        StockLevelDto item = client.createItem(5L, new CreateStockItemRequestDto(10, "admin", RESERVATION));
        LedgerMovementDto movement = client.adjust(new AdjustStockRequestDto(5L, -3, "Damaged", RESERVATION, "admin"));
        client.deleteItem(5L);

        assertThat(confirmed.lines()).isEmpty();
        assertThat(item).isEqualTo(new StockLevelDto(5L, 10, 1L));
        assertThat(movement).isEqualTo(new LedgerMovementDto(7L, 5L, null, InventoryMovementType.ADMIN_ADJUSTMENT,
                -3, 7, "admin", "Damaged", RESERVATION, LocalDateTime.of(2026, 11, 14, 10, 0, 0, 123_456_000), 43L));
        warehouse.verify(postRequestedFor(urlEqualTo("/internal/v1/stock/reservations/" + RESERVATION + "/confirm"))
                .withRequestBody(equalToJson("{\"orderId\":123}")));
        warehouse.verify(putRequestedFor(urlEqualTo("/internal/v1/stock/items/5"))
                .withRequestBody(equalToJson("{\"initialQuantity\":10,\"actor\":\"admin\",\"requestId\":\""
                        + RESERVATION + "\"}")));
        warehouse.verify(postRequestedFor(urlEqualTo("/internal/v1/stock/adjustments"))
                .withRequestBody(equalToJson("{\"productId\":5,\"delta\":-3,\"reason\":\"Damaged\",\"requestId\":\""
                        + RESERVATION + "\",\"actor\":\"admin\"}")));
        warehouse.verify(deleteRequestedFor(urlEqualTo("/internal/v1/stock/items/5")));
    }

    @Test
    void movementsReadAFlatSpringPage() {
        warehouse.stubFor(get("/internal/v1/stock/movements?productId=5&page=1&size=2").willReturn(json(200, """
                {"content":[{"id":9,"productId":5,"orderId":3,"type":"ORDER_DEDUCTED","delta":-1,"quantityAfter":4,
                 "actor":"bob","reason":"Order deduction","requestId":null,"createdAt":"2026-11-14T10:00:00"}],
                 "totalElements":3,"number":1,"size":2}
                """)));

        Page<InventoryMovementDto> page = client.movements(5L, 1, 2);

        assertThat(page.getContent()).singleElement().satisfies(movement -> {
            assertThat(movement.getId()).isEqualTo(9L);
            assertThat(movement.getOrderId()).isEqualTo(3L);
            assertThat(movement.getRequestId()).isNull();
        });
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void movementsReadAPagedModelAndFallBackToTheContentSize() {
        warehouse.stubFor(get("/internal/v1/stock/movements?productId=5&page=0&size=20").willReturn(json(200, """
                {"content":[],"page":{"size":20,"number":0,"totalElements":41,"totalPages":3}}
                """)));
        warehouse.stubFor(get("/internal/v1/stock/movements?productId=6&page=0&size=20").willReturn(json(200, """
                {"content":[{"id":1,"type":"INITIAL_STOCK"}]}
                """)));

        assertThat(client.movements(5L, 0, 20).getTotalElements()).isEqualTo(41);
        assertThat(client.movements(6L, 0, 20).getTotalElements()).isEqualTo(1);
    }

    @Test
    void conflictsAndNotFoundBecomeRejectionsWithWarehouseServicesMessageAndProduct() {
        warehouse.stubFor(post("/internal/v1/stock/reservations").willReturn(json(409,
                "{\"message\":\"Insufficient stock for product 1\",\"productId\":1}")));
        warehouse.stubFor(post("/internal/v1/stock/adjustments").willReturn(json(404, "{\"message\":\"Product not found\"}")));
        warehouse.stubFor(post(urlPathMatching(".*/confirm")).willReturn(aResponse().withStatus(409)));
        warehouse.stubFor(post(urlPathMatching(".*/release")).willReturn(json(404, "not json")));

        assertThatThrownBy(() -> client.reserve(RESERVATION, "alice", List.of(new StockLineDto(1L, 9))))
                .isInstanceOfSatisfying(StockLedgerRejection.class, rejection -> {
                    assertThat(rejection.status()).isEqualTo(409);
                    assertThat(rejection.isConflict()).isTrue();
                    assertThat(rejection.isNotFound()).isFalse();
                    assertThat(rejection.getMessage()).isEqualTo("Insufficient stock for product 1");
                    assertThat(rejection.productId()).isEqualTo(1L);
                });
        assertThatThrownBy(() -> client.adjust(new AdjustStockRequestDto(1L, 1, "x", RESERVATION, "admin")))
                .isInstanceOfSatisfying(StockLedgerRejection.class, rejection -> {
                    assertThat(rejection.isNotFound()).isTrue();
                    assertThat(rejection.isConflict()).isFalse();
                    assertThat(rejection.getMessage()).isEqualTo("Product not found");
                    assertThat(rejection.productId()).isNull();
                });
        assertThatThrownBy(() -> client.confirm(RESERVATION, 1L))
                .isInstanceOfSatisfying(StockLedgerRejection.class, rejection -> {
                    assertThat(rejection.status()).isEqualTo(409);
                    assertThat(rejection.getMessage()).isNull();
                });
        assertThatThrownBy(() -> client.release(RESERVATION))
                .isInstanceOfSatisfying(StockLedgerRejection.class, rejection -> {
                    assertThat(rejection.status()).isEqualTo(404);
                    assertThat(rejection.getMessage()).isNull();
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504, 400, 401, 403, 410, 422, 300, 302})
    void serverErrorsAndUnexpectedStatusesAreUnavailable(int status) {
        warehouse.stubFor(any(urlPathMatching("/.*")).willReturn(json(status, "{\"message\":\"Stock ledger not initialised\"}")));

        assertUnavailable(() -> client.reserve(RESERVATION, "alice", List.of(new StockLineDto(1L, 1))));
    }

    @Test
    void aSlowWarehouseIsUnavailableAfterTheReadTimeout() {
        warehouse.stubFor(any(urlPathMatching("/.*")).willReturn(json(201, "{}").withFixedDelay(1500)));

        long started = System.nanoTime();
        assertUnavailable(() -> client.release(RESERVATION));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1400));
    }

    @Test
    void connectionResetsRefusedConnectionsAndUnreadableBodiesAreUnavailable() throws Exception {
        warehouse.stubFor(post("/internal/v1/stock/reservations")
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        warehouse.stubFor(put("/internal/v1/stock/items/1").willReturn(json(201, "{not json")));
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        StockLedgerClient refused = new StockLedgerClient("http://127.0.0.1:" + closedPort, Duration.ofMillis(500),
                Duration.ofMillis(500), TOKEN, CorrelationTracing.disabled());

        assertUnavailable(() -> client.reserve(RESERVATION, "alice", List.of(new StockLineDto(1L, 1))));
        assertUnavailable(() -> client.createItem(1L, new CreateStockItemRequestDto(1, "admin", RESERVATION)));
        assertUnavailable(() -> refused.deleteItem(1L));
    }

    @Test
    void anEmptySuccessfulBodyIsAcceptedWhereNoReplyIsRead() {
        warehouse.stubFor(delete("/internal/v1/stock/items/3").willReturn(aResponse().withStatus(204)));
        warehouse.stubFor(post(urlPathMatching(".*/release")).willReturn(aResponse().withStatus(200)));

        client.deleteItem(3L);
        client.release(RESERVATION);

        warehouse.verify(deleteRequestedFor(urlEqualTo("/internal/v1/stock/items/3")));
    }

    @Test
    void aCallInsideADatabaseTransactionIsRefusedBeforeAnyRequest() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> client.release(RESERVATION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must not be called while a database transaction is open");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(warehouse.getAllServeEvents()).isEmpty();
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CustomException.class, error -> {
            assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(error.getMessage()).isEqualTo("Inventory service unavailable");
        });
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }
}
