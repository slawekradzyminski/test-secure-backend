package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.inventory.InventoryMovementRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({InventoryService.class, OrderService.class})
class InventoryConcurrencyIT {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                                    "postgres:16-alpine@sha256:"
                                            + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                            .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        movementRepository.deleteAll();
        cartItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onlyOneBuyerCanCheckoutTheFinalUnit() throws Exception {
        ProductEntity product = createProductAndCarts(1, List.of("buyer-one", "buyer-two"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<CheckoutOutcome> first = executor.submit(
                    () -> checkout("buyer-one", ready, start));
            Future<CheckoutOutcome> second = executor.submit(
                    () -> checkout("buyer-two", ready, start));

            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, SECONDS), second.get(10, SECONDS)))
                    .containsExactlyInAnyOrder(CheckoutOutcome.CREATED, CheckoutOutcome.STOCK_CONFLICT);
        }

        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(cartItemRepository.count()).isEqualTo(1);
        assertThat(movementRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSubmissionsOfTheSameCartCreateOnlyOneOrder() throws Exception {
        ProductEntity product = createProductAndCarts(2, List.of("same-buyer"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<CheckoutOutcome> first = executor.submit(
                    () -> checkout("same-buyer", ready, start));
            Future<CheckoutOutcome> second = executor.submit(
                    () -> checkout("same-buyer", ready, start));

            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, SECONDS), second.get(10, SECONDS)))
                    .containsExactlyInAnyOrder(CheckoutOutcome.CREATED, CheckoutOutcome.CART_EMPTY);
        }

        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(1);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(cartItemRepository.count()).isZero();
        assertThat(movementRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unavailableLineRollsBackTheEntireCheckout() {
        ProductEntity available = createProductAndCarts(2, List.of("multi-line-buyer"));
        ProductEntity unavailable = createProductAndCarts(0, List.of("multi-line-buyer"));

        assertThatThrownBy(() -> orderService.createOrder("multi-line-buyer", address()))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getHttpStatus().value())
                        .isEqualTo(409));

        assertThat(productRepository.findById(available.getId()).orElseThrow().getStockQuantity()).isEqualTo(2);
        assertThat(productRepository.findById(unavailable.getId()).orElseThrow().getStockQuantity()).isZero();
        assertThat(orderRepository.count()).isZero();
        assertThat(cartItemRepository.count()).isEqualTo(2);
        assertThat(movementRepository.count()).isZero();
    }

    private ProductEntity createProductAndCarts(int stock, List<String> usernames) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            ProductEntity product = productRepository.save(ProductEntity.builder()
                    .name("Last unit product")
                    .description("Inventory concurrency test")
                    .price(BigDecimal.TEN)
                    .stockQuantity(stock)
                    .category("Tests")
                    .build());
            usernames.forEach(username -> cartItemRepository.save(CartItemEntity.builder()
                    .username(username)
                    .product(product)
                    .quantity(1)
                    .price(product.getPrice())
                    .version(0L)
                    .build()));
            return product;
        });
    }

    private CheckoutOutcome checkout(
            String username,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            orderService.createOrder(username, address());
            return CheckoutOutcome.CREATED;
        } catch (CustomException exception) {
            if (exception.getHttpStatus().value() == 409) {
                return CheckoutOutcome.STOCK_CONFLICT;
            }
            if (exception.getHttpStatus().value() == 400 && "Cart is empty".equals(exception.getMessage())) {
                return CheckoutOutcome.CART_EMPTY;
            }
            throw exception;
        }
    }

    private AddressDto address() {
        return AddressDto.builder()
                .street("Main Street 1")
                .city("Warsaw")
                .state("Mazowieckie")
                .zipCode("00-001")
                .country("Poland")
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new IllegalStateException("Timed out waiting for inventory concurrency test");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Inventory concurrency test was interrupted", exception);
        }
    }

    private enum CheckoutOutcome {
        CREATED,
        STOCK_CONFLICT,
        CART_EMPTY
    }
}
