package com.awesome.testing.service;

import com.awesome.testing.config.CommerceClockConfig;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.internal.service.InternalOrderService;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.stock.StockReservationClaimRepository;
import com.awesome.testing.service.checkout.CheckoutContributors;
import com.awesome.testing.service.coupon.CouponService;
import com.awesome.testing.service.reservation.OrderReservations;
import com.awesome.testing.stock.FakeStockLedger;
import com.awesome.testing.stock.StockSliceConfig;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checkout versus warehouse-service's reservation reaper on PostgreSQL (V26 {@code stock_reservation_claims}):
 * the reaper's lookup ({@link InternalOrderService#byReservation}) and the order transaction can no longer
 * both conclude "no order" and "order stored" for one reservation.
 */
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({InventoryService.class, OrderService.class, InternalOrderService.class, CouponService.class,
        CommerceClockConfig.class, OrderReservations.class, CheckoutContributors.class, CommerceEvents.class,
        StockSliceConfig.class})
class CheckoutReaperRaceIT {

    private static final String BUYER = "race-buyer";

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:16-alpine@sha256:"
                                            + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                            .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired private OrderService orderService;
    @Autowired private InternalOrderService internalOrderService;
    @Autowired private StockReservationClaimRepository claims;
    @Autowired private ProductRepository productRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private FakeStockLedger stockLedger;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @BeforeEach
    void cleanDatabase() {
        claims.deleteAll();
        cartItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aLookupDuringASlowOrderSaveWaitsAndFindsTheOrder() throws Exception {
        createProductAndCart();
        CountDownLatch cartLocked = new CountDownLatch(1);
        CountDownLatch unlockCart = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Another transaction holds the buyer's cart rows: the order transaction stalls after its claim.
            Future<?> lock = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                cartItemRepository.findByUsernameForUpdate(BUYER);
                cartLocked.countDown();
                await(unlockCart);
            }));
            assertThat(cartLocked.await(5, SECONDS)).isTrue();
            Future<Long> checkout = executor.submit(() -> orderService.createOrder(BUYER, address()).getId());
            UUID reservation = awaitReservation();
            Thread.sleep(300); // the order transaction is now waiting for the cart rows

            Future<Long> lookup = executor.submit(() -> internalOrderService.byReservation(reservation).orderId());
            assertThatThrownBy(() -> lookup.get(1, SECONDS))
                    .as("the lookup waits for the order transaction instead of answering 404")
                    .isInstanceOf(TimeoutException.class);

            unlockCart.countDown();
            lock.get(5, SECONDS);
            Long orderId = checkout.get(10, SECONDS);
            assertThat(lookup.get(10, SECONDS)).isEqualTo(orderId);
        }
        assertThat(claims.count()).as("no tombstone is left for an existing order").isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aLookupThatFoundNoOrderKeepsATombstoneThatStopsTheOrder() {
        UUID reservation = UUID.randomUUID();

        assertThatThrownBy(() -> internalOrderService.byReservation(reservation))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(claims.existsById(reservation)).isTrue();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(s -> claims.insert(reservation, Instant.now())))
                .as("a checkout still saving this reservation's order can no longer claim it")
                .isInstanceOf(DataIntegrityViolationException.class);

        // A second reaper run answers 404 again and keeps the single tombstone.
        assertThatThrownBy(() -> internalOrderService.byReservation(reservation)).isInstanceOf(CustomException.class);
        assertThat(claims.count()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aCheckoutLeavesNoClaimBehind() {
        createProductAndCart();

        Long orderId = orderService.createOrder(BUYER, address()).getId();

        assertThat(orderRepository.findById(orderId)).isPresent();
        assertThat(claims.count()).isZero();
    }

    private UUID awaitReservation() throws Exception {
        for (int i = 0; i < 100; i++) {
            var requests = stockLedger.requests(postRequestedFor(urlEqualTo("/internal/v1/stock/reservations")));
            if (!requests.isEmpty()) {
                String body = requests.getLast().getBodyAsString();
                return UUID.fromString(new ObjectMapper().readTree(body).get("reservationId").asText());
            }
            MILLISECONDS.sleep(50);
        }
        throw new AssertionError("checkout never reserved");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void createProductAndCart() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ProductEntity product = productRepository.save(ProductEntity.builder()
                    .name("Race product")
                    .description("Checkout versus reaper")
                    .price(BigDecimal.TEN)
                    .stockQuantity(5)
                    .category("Tests")
                    .build());
            cartItemRepository.save(CartItemEntity.builder()
                    .username(BUYER)
                    .product(product)
                    .quantity(1)
                    .price(product.getPrice())
                    .version(0L)
                    .build());
        });
    }

    private static AddressDto address() {
        return AddressDto.builder()
                .street("Main Street 1")
                .city("Warsaw")
                .state("Mazowieckie")
                .zipCode("00-001")
                .country("Poland")
                .build();
    }
}
