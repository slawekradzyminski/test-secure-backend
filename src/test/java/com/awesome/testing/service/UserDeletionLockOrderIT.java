package com.awesome.testing.service;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.email.EmailEventService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static com.awesome.testing.factory.ProductFactory.getRandomProduct;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-USER-DELETE-DEADLOCK: a payment result locks the order row and then share-locks the user row to
 * record the payment email. Deleting the user locked the user row first and then the order rows, so the
 * two transactions waited on each other and PostgreSQL aborted one of them (500 on DELETE /users/{name}).
 * Deletion now locks the user's cart and order rows before the user row, the same order as checkout and
 * payment results.
 */
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
class UserDeletionLockOrderIT extends DomainHelper {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine@sha256:"
                    + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private UserService userService;
    @Autowired
    private EmailEventService emailEventService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
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
    void deletingAUserWhileAPaymentResultHoldsTheirOrderDoesNotDeadlock() throws Exception {
        // given: a user with an order
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(client);
        ProductEntity product = productRepository.saveAndFlush(getRandomProduct());
        executePost("/api/v1/cart/items", Map.of("productId", product.getId(), "quantity", 1), getHeadersWith(token),
                String.class);
        ResponseEntity<OrderDto> checkout = executePost("/api/v1/orders", getRandomAddress(), getHeadersWith(token),
                OrderDto.class);
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long orderId = checkout.getBody().getId();
        UserEntity user = userRepository.findByUsername(client.getUsername()).orElseThrow();
        EmailDto email = EmailDto.builder().to(user.getEmail()).subject("Payment received").message("Body")
                .template(EmailTemplate.GENERIC).build();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch orderLocked = new CountDownLatch(1);

        // when: the payment result holds the order lock while the deletion starts, then records its email
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Throwable> payment = executor.submit(() -> {
                try {
                    transaction.executeWithoutResult(status -> {
                        orderRepository.findByIdForUpdate(orderId).orElseThrow();
                        orderLocked.countDown();
                        awaitBlockedTransaction();
                        emailEventService.recordQueued(user, email, "email", null);
                    });
                    return null;
                } catch (RuntimeException e) {
                    return e;
                } finally {
                    orderLocked.countDown();
                }
            });
            assertThat(orderLocked.await(10, SECONDS)).isTrue();
            Future<Throwable> deletion = executor.submit(() -> {
                try {
                    userService.forget(client.getUsername());
                    return null;
                } catch (RuntimeException e) {
                    return e;
                }
            });

            // then
            assertThat(payment.get(30, SECONDS)).as("payment transaction").isNull();
            assertThat(deletion.get(30, SECONDS)).as("user deletion").isNull();
        }
        assertThat(userRepository.findByUsername(client.getUsername())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM email_event WHERE user_id = ?", Long.class,
                user.getId())).isZero();
    }

    /** Waits until another transaction (the deletion) is blocked on a row lock. */
    private void awaitBlockedTransaction() {
        long deadline = System.nanoTime() + SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long waiting = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Long.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("The deletion never waited for a lock");
    }
}
