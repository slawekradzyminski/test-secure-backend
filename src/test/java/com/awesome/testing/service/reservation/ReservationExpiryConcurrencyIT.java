package com.awesome.testing.service.reservation;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.payment.PaymentResultDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.entity.payment.OrderPaymentStatus;
import com.awesome.testing.internal.service.InternalPaymentService;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.CartService;
import com.awesome.testing.service.OrderService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reservation expiry on PostgreSQL row locks: the order's row lock decides between expiry and a capture or a
 * customer cancel, and several job instances never cancel an order twice.
 */
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
class ReservationExpiryConcurrencyIT extends DomainHelper {

    private static final AddressDto ADDRESS = new AddressDto("Main Street 1", "Warsaw", "Mazovia", "00-001", "Poland");
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine@sha256:"
                    + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private OrderService orderService;
    @Autowired
    private ReservationExpiryJob job;
    @Autowired
    private InternalPaymentService paymentService;
    @Autowired
    private CartService cartService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void aCaptureWaitingForTheExpiringOrderIsRejectedWith409() throws Exception {
        ProductEntity product = product(5);
        OrderDto order = order(user(), product, 1);
        makeDue(order.getId());
        CountDownLatch expiryHoldsTheLock = new CountDownLatch(1);
        CountDownLatch commitExpiry = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> expiry = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                boolean expired = orderService.expireIfDue(order.getId());
                expiryHoldsTheLock.countDown();
                await(commitExpiry);
                return expired;
            }));
            assertThat(expiryHoldsTheLock.await(10, SECONDS)).isTrue();
            Future<?> capture = executor.submit(() -> paymentService.apply(order.getId(), new PaymentResultDto(
                    UUID.randomUUID(), UUID.randomUUID(), OrderPaymentStatus.CAPTURED, order.getTotalAmount(), "USD", null)));

            assertThatThrownBy(() -> capture.get(500, MILLISECONDS))
                    .as("the capture waits for the order row lock").isInstanceOf(TimeoutException.class);
            commitExpiry.countDown();

            assertThat(expiry.get(10, SECONDS)).isTrue();
            assertThatThrownBy(() -> capture.get(10, SECONDS)).isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOfSatisfying(CustomException.class,
                            e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT));
        }
        assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(count("SELECT count(*) FROM order_payments WHERE order_id = ? AND status = 'CAPTURED'", order.getId()))
                .isZero();
        assertThat(stockOf(product)).isEqualTo(5);
    }

    @Test
    void customerCancelAndExpiryRacingRestoreTheStockExactlyOnce() throws Exception {
        String user = user();
        ProductEntity product = product(1_000);
        int expiryWins = 0;
        int cancelWins = 0;
        for (int round = 0; round < 20; round++) {
            OrderDto order = order(user, product, 2);
            makeDue(order.getId());
            CountDownLatch start = new CountDownLatch(1);

            boolean expired;
            boolean cancelled;
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<Boolean> expiry = executor.submit(() -> {
                    await(start);
                    return orderService.expireIfDue(order.getId());
                });
                Future<Boolean> cancel = executor.submit(() -> {
                    await(start);
                    try {
                        orderService.cancelOrder(order.getId(), user, false);
                        return true;
                    } catch (CustomException e) {
                        assertThat(e.getMessage()).isEqualTo("Order cannot be cancelled in current status");
                        return false;
                    }
                });
                start.countDown();
                expired = expiry.get(20, SECONDS);
                cancelled = cancel.get(20, SECONDS);
            }

            assertThat(expired ^ cancelled).as("exactly one side cancels round %d", round).isTrue();
            expiryWins += expired ? 1 : 0;
            cancelWins += cancelled ? 1 : 0;
            assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELLED);
            assertThat(restorations(order.getId())).isEqualTo(1);
            assertThat(events(order.getId(), "order.cancelled")).isEqualTo(1);
            assertThat(events(order.getId(), "order.reservation-expired")).isEqualTo(expired ? 1 : 0);
            assertThat(jdbcTemplate.queryForObject("SELECT payload FROM domain_events WHERE order_id = ? "
                    + "AND type = 'order.cancelled'", String.class, order.getId()))
                    .contains(expired ? "\"reason\":\"RESERVATION_EXPIRED\"" : "\"reason\":\"CUSTOMER\"");
        }
        assertThat(stockOf(product)).isEqualTo(1_000);
        assertThat(expiryWins + cancelWins).isEqualTo(20);
    }

    @Test
    void severalJobInstancesCancelEveryDueOrderOnce() throws Exception {
        String user = user();
        ProductEntity product = product(500);
        List<OrderDto> orders = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            orders.add(order(user, product, 1));
        }
        orders.forEach(order -> makeDue(order.getId()));
        CountDownLatch start = new CountDownLatch(1);

        int total = 0;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> instances = new ArrayList<>();
            for (int instance = 0; instance < 3; instance++) {
                instances.add(executor.submit(() -> {
                    await(start);
                    return job.expireDue();
                }));
            }
            start.countDown();
            for (Future<Integer> instance : instances) {
                total += instance.get(60, SECONDS);
            }
        }
        total += job.expireDue();

        assertThat(total).isEqualTo(30);
        assertThat(orders).allSatisfy(order -> {
            assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELLED);
            assertThat(restorations(order.getId())).isEqualTo(1);
            assertThat(events(order.getId(), "order.reservation-expired")).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT actor FROM inventory_movements WHERE order_id = ? "
                    + "AND type = 'ORDER_RESTORED'", String.class, order.getId())).isEqualTo("system");
        });
        assertThat(stockOf(product)).isEqualTo(500);
        assertThat(job.expireDue()).isZero();
    }

    @Test
    void theJobSkipsAnOrderLockedByAnotherTransactionInsteadOfWaiting() throws Exception {
        ProductEntity product = product(5);
        OrderDto order = order(user(), product, 1);
        makeDue(order.getId());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                orderRepository.findByIdForUpdate(order.getId()).orElseThrow();
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(10, SECONDS)).isTrue();

            Future<Boolean> skipped = executor.submit(() -> orderService.expireIfDue(order.getId()));
            assertThat(skipped.get(5, SECONDS)).as("SKIP LOCKED returns at once").isFalse();
            assertThat(statusOf(order)).isEqualTo(OrderStatus.PENDING);

            release.countDown();
            holder.get(10, SECONDS);
        }
        assertThat(orderService.expireIfDue(order.getId())).isTrue();
        assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELLED);
    }

    private OrderDto order(String username, ProductEntity product, int quantity) {
        cartService.addToCart(username, new CartItemDto(product.getId(), quantity));
        return orderService.createOrder(username, ADDRESS);
    }

    private void makeDue(Long orderId) {
        jdbcTemplate.update("UPDATE orders SET reservation_expires_at = now() - interval '1 second' WHERE id = ?", orderId);
    }

    private ProductEntity product(int stock) {
        return productRepository.saveAndFlush(ProductEntity.builder()
                .name("Reserved " + UUID.randomUUID()).description("Reservation race product")
                .price(new BigDecimal("10.00")).stockQuantity(stock).category("Tests").build());
    }

    private String user() {
        String name = "res-" + UUID.randomUUID().toString().substring(0, 12);
        userRepository.saveAndFlush(UserEntity.builder().username(name).email(name + "@example.com")
                .password("secret-password").firstName("Res").lastName("Erve").build());
        return name;
    }

    private OrderStatus statusOf(OrderDto order) {
        return orderRepository.findById(order.getId()).orElseThrow().getStatus();
    }

    private int stockOf(ProductEntity product) {
        return productRepository.findById(product.getId()).orElseThrow().getStockQuantity();
    }

    private long restorations(Long orderId) {
        return count("SELECT count(*) FROM inventory_movements WHERE order_id = ? AND type = 'ORDER_RESTORED'", orderId);
    }

    private long events(Long orderId, String type) {
        return count("SELECT count(*) FROM domain_events WHERE order_id = ? AND type = ?", orderId, type);
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
