package com.awesome.testing.grpc;

import com.awesome.testing.grpc.proto.GetStockRequest;
import com.awesome.testing.grpc.proto.ListStockMovementsRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
class InventoryGrpcConcurrencyIT extends AbstractInventoryGrpcTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine@sha256:"
                    + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void concurrentRetriesCommitOneMovement() throws Exception {
        // given
        var product = setupProduct();
        var token = admin();
        var request = adjustment(product.getId(), 3);

        // when
        var results = concurrently(() -> stub(token).adjustStock(request), () -> stub(token).adjustStock(request));

        // then
        assertThat(results.getFirst()).isEqualTo(results.getLast());
        assertThat(stub(token).getStock(GetStockRequest.newBuilder().setProductId(product.getId()).build()).getAvailableQuantity())
                .isEqualTo(product.getStockQuantity() + 3);
        assertThat(stub(token).listStockMovements(ListStockMovementsRequest.newBuilder().setProductId(product.getId()).build())
                .getTotal()).isEqualTo(1);
    }

    @Test
    void concurrentDeductionsCannotDriveStockBelowZero() throws Exception {
        // given
        var product = setupProduct();
        product.setStockQuantity(1);
        productRepository.saveAndFlush(product);
        var token = admin();
        var first = adjustment(product.getId(), -1);
        var second = adjustment(product.getId(), -1);

        // when
        var results = concurrently(() -> outcome(() -> stub(token).adjustStock(first)),
                () -> outcome(() -> stub(token).adjustStock(second)));

        // then
        assertThat(results).containsExactlyInAnyOrder(Status.Code.OK, Status.Code.FAILED_PRECONDITION);
        assertThat(inventory.get(product.getId(), 10).getAvailableQuantity()).isZero();
        assertThat(stub(token).listStockMovements(ListStockMovementsRequest.newBuilder().setProductId(product.getId()).build())
                .getTotal()).isEqualTo(1);
    }

    private Status.Code outcome(Runnable operation) {
        try {
            operation.run();
            return Status.Code.OK;
        } catch (StatusRuntimeException exception) {
            return exception.getStatus().getCode();
        }
    }

    private <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstResult = executor.submit(() -> afterStart(first, ready, start));
            var secondResult = executor.submit(() -> afterStart(second, ready, start));
            try {
                assertThat(ready.await(5, SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            return List.of(firstResult.get(15, SECONDS), secondResult.get(15, SECONDS));
        }
    }

    private <T> T afterStart(Callable<T> operation, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, SECONDS)).isTrue();
        return operation.call();
    }
}
