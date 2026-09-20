package com.awesome.testing.graphql;

import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.repository.inventory.InventoryMovementRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
class GraphQlConcurrencyIT extends AbstractCommerceGraphQlTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine@sha256:"
                    + "57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private InventoryMovementRepository movements;

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
    void concurrentGraphQlCheckoutsCannotSellTheSameFinalUnit() throws Exception {
        // given
        var alice = customer();
        var bob = customer();
        var product = product("19.99", 1);
        cartService.addToCart(alice.username(), new CartItemDto(product.getId(), 1));
        cartService.addToCart(bob.username(), new CartItemDto(product.getId(), 1));
        String checkout = "mutation($address:AddressInput!){checkout(address:$address){id}}";

        // when
        var outcomes = concurrently(
                () -> query(alice.token(), checkout, Map.of("address", ADDRESS_INPUT)),
                () -> query(bob.token(), checkout, Map.of("address", ADDRESS_INPUT)));

        // then
        assertThat(outcomes.stream().filter(result -> result.path("errors").isMissingNode())).hasSize(1);
        assertThat(outcomes.stream().filter(result -> result.has("errors")).toList()).singleElement()
                .satisfies(result -> assertError(result, "CONFLICT"));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(cartItemRepository.count()).isEqualTo(1);
        assertThat(movements.count()).isEqualTo(1);
    }

    @Test
    void concurrentAdjustmentRetriesCreateExactlyOneMovement() throws Exception {
        // given
        var administrator = admin();
        var product = product("19.99", 5);
        String document = "mutation($id:ID!,$input:InventoryAdjustmentInput!){adjustInventory(productId:$id,input:$input){id quantityAfter}}";
        var variables = Map.<String, Object>of("id", product.getId(), "input",
                Map.of("delta", 3, "reason", "Concurrent restock", "requestId", UUID.randomUUID().toString()));

        // when
        var outcomes = concurrently(
                () -> query(administrator.token(), document, variables),
                () -> query(administrator.token(), document, variables));

        // then
        assertThat(outcomes).allSatisfy(result -> assertThat(result.has("errors")).isFalse());
        assertThat(outcomes.getFirst().at("/data/adjustInventory")).isEqualTo(outcomes.getLast().at("/data/adjustInventory"));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(movements.count()).isEqualTo(1);
    }

    private List<JsonNode> concurrently(Callable<JsonNode> first, Callable<JsonNode> second) throws Exception {
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

    private JsonNode afterStart(Callable<JsonNode> operation, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, SECONDS)).isTrue();
        return operation.call();
    }
}
