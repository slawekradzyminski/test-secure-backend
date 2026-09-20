package com.awesome.testing.grpc;

import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.grpc.proto.AdjustStockRequest;
import com.awesome.testing.grpc.proto.GetStockRequest;
import com.awesome.testing.grpc.proto.ListInventoryRequest;
import com.awesome.testing.grpc.proto.ListStockMovementsRequest;
import com.awesome.testing.grpc.proto.StockStatus;
import com.awesome.testing.repository.UserRepository;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class InventoryGrpcTest extends AbstractInventoryGrpcTest {
    @Autowired
    private UserRepository users;

    @Test
    void rejectsMissingInvalidAndCustomerTokensForEveryRpc() {
        // given
        var customer = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        var request = GetStockRequest.newBuilder().setProductId(1).build();

        // when / then
        assertStatus(Status.Code.UNAUTHENTICATED, () -> stub(null).getStock(request));
        assertStatus(Status.Code.UNAUTHENTICATED, () -> stub("invalid.token").getStock(request));
        assertStatus(Status.Code.PERMISSION_DENIED, () -> stub(customer).getStock(request));
        assertStatus(Status.Code.PERMISSION_DENIED, () -> stub(customer).listInventory(ListInventoryRequest.getDefaultInstance()));
        assertStatus(Status.Code.PERMISSION_DENIED, () -> stub(customer).adjustStock(AdjustStockRequest.getDefaultInstance()));
        assertStatus(Status.Code.PERMISSION_DENIED, () -> stub(customer).listStockMovements(ListStockMovementsRequest.getDefaultInstance()));
        assertStatus(Status.Code.UNAUTHENTICATED, () -> HealthGrpc.newBlockingStub(channel)
                .withDeadlineAfter(5, TimeUnit.SECONDS).check(HealthCheckRequest.getDefaultInstance()));
    }

    @Test
    void adminCanReadInventoryAndIdentityDoesNotLeakToTheNextCall() {
        // given
        var product = setupProduct();
        var admin = admin();
        var request = GetStockRequest.newBuilder().setProductId(product.getId()).build();

        // when
        var item = stub(admin).getStock(request);
        var page = stub(admin).listInventory(ListInventoryRequest.getDefaultInstance());

        // then
        assertThat(item.getProductId()).isEqualTo(product.getId());
        assertThat(item.getAvailableQuantity()).isEqualTo(product.getStockQuantity());
        assertThat(item.getName()).isEqualTo(product.getName());
        assertThat(page.getItemsList()).containsExactly(item);
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getPage()).isZero();
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(stub(admin).listInventory(ListInventoryRequest.newBuilder().setSize(100).build()).getSize()).isEqualTo(100);
        assertStatus(Status.Code.UNAUTHENTICATED, () -> stub(null).getStock(request));
    }

    @Test
    void adjustmentReplayIsIdempotentAuditedAndVisibleToRestAndGraphql() {
        // given
        var user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        var token = getToken(user);
        var product = setupProduct();
        var request = adjustment(product.getId(), 3);
        var client = stub(token);

        // when
        var first = client.adjustStock(request);
        var retry = client.adjustStock(request);
        var rest = executeGet("/api/v1/products/" + product.getId(), getHeadersWith(token), String.class);
        var graphql = executePost("/api/v1/graphql", Map.of("query", "{inventoryItem(productId:"
                + product.getId() + "){availableQuantity}}"), getHeadersWith(token), String.class);
        var history = client.listStockMovements(ListStockMovementsRequest.newBuilder().setProductId(product.getId()).build());

        // then
        assertThat(retry).isEqualTo(first);
        assertThat(first.getQuantityAfter()).isEqualTo(product.getStockQuantity() + 3);
        assertThat(first.getActor()).isEqualTo(user.getUsername());
        assertThat(first.getRequestId()).isEqualTo(request.getRequestId());
        assertThat(first.hasOrderId()).isFalse();
        assertThat(first.getType()).isEqualTo("ADMIN_ADJUSTMENT");
        assertThat(first.getReason()).isEqualTo(request.getReason());
        assertThat(first.getDelta()).isEqualTo(3);
        assertThat(first.getCreatedAt()).isNotBlank();
        assertThat(history.getItemsList()).containsExactly(first);
        assertThat(history.getTotal()).isEqualTo(1);
        assertThat(mapper.readTree(rest.getBody()).path("stockQuantity").asInt()).isEqualTo(first.getQuantityAfter());
        assertThat(mapper.readTree(graphql.getBody()).has("errors")).isFalse();
        assertThat(mapper.readTree(graphql.getBody()).at("/data/inventoryItem/availableQuantity").asInt())
                .isEqualTo(first.getQuantityAfter());
    }

    @Test
    void rejectsPayloadMismatchAndInsufficientStockWithoutChangingInventory() {
        // given
        var product = setupProduct();
        var client = stub(admin());
        var request = adjustment(product.getId(), 1);
        var first = client.adjustStock(request);

        // when / then
        assertStatus(Status.Code.FAILED_PRECONDITION, () -> client.adjustStock(request.toBuilder().setDelta(2).build()));
        assertStatus(Status.Code.FAILED_PRECONDITION, () -> client.adjustStock(request.toBuilder().setReason("Changed").build()));
        assertStatus(Status.Code.FAILED_PRECONDITION, () -> client.adjustStock(adjustment(product.getId(), Integer.MIN_VALUE)));
        assertThat(client.getStock(GetStockRequest.newBuilder().setProductId(product.getId()).build()).getAvailableQuantity())
                .isEqualTo(first.getQuantityAfter());
    }

    @Test
    void absentPaginationAndThresholdUseDefaultsButExplicitInvalidValuesAreRejected() {
        // given
        var product = setupProduct();
        product.setStockQuantity(5);
        productRepository.saveAndFlush(product);
        var client = stub(admin());
        var request = GetStockRequest.newBuilder().setProductId(product.getId());

        // when / then
        assertThat(client.getStock(request.build()).getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(client.getStock(request.setLowStockThreshold(4).build()).getStockStatus()).isEqualTo(StockStatus.IN_STOCK);
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.getStock(request.setLowStockThreshold(0).build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.getStock(request.setProductId(0).build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.listInventory(ListInventoryRequest.newBuilder().setSize(0).build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.listInventory(ListInventoryRequest.newBuilder().setSize(101).build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.listInventory(ListInventoryRequest.newBuilder().setPage(-1).build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.listInventory(ListInventoryRequest.newBuilder().setStatusValue(99).build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.listInventory(ListInventoryRequest.newBuilder().setStatus(StockStatus.STOCK_STATUS_UNSPECIFIED).build()));
        assertStatus(Status.Code.NOT_FOUND, () -> client.getStock(GetStockRequest.newBuilder().setProductId(Long.MAX_VALUE).build()));
    }

    @Test
    void validatesAdjustmentsAndDoesNotExecuteAnExpiredDeadline() {
        // given
        var product = setupProduct();
        var client = stub(admin());
        var request = adjustment(product.getId(), 1);

        // when / then
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.adjustStock(request.toBuilder().setDelta(0).build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.adjustStock(request.toBuilder().setProductId(0).build()));
        assertStatus(Status.Code.NOT_FOUND, () -> client.adjustStock(request.toBuilder().setProductId(Long.MAX_VALUE).build()));
        assertStatus(Status.Code.NOT_FOUND, () -> client.listStockMovements(ListStockMovementsRequest.newBuilder().setProductId(Long.MAX_VALUE).build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.listStockMovements(ListStockMovementsRequest.newBuilder().setProductId(0).build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.adjustStock(request.toBuilder().setReason(" ").build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.adjustStock(request.toBuilder().setReason("x".repeat(501)).build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.adjustStock(request.toBuilder().setRequestId("1-1-1-1-1").build()));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.adjustStock(request.toBuilder().clearRequestId().build()));
        assertStatus(Status.Code.DEADLINE_EXCEEDED, () -> client.withDeadlineAfter(-1, TimeUnit.SECONDS).adjustStock(request));
        assertThat(inventory.get(product.getId(), 10).getAvailableQuantity()).isEqualTo(product.getStockQuantity());
    }

    @Test
    void readsRestAndGraphqlAdjustmentsAndPaginatesMovements() {
        // given
        var product = setupProduct();
        var token = admin();
        var rest = executePost("/api/v1/admin/inventory/" + product.getId() + "/adjustments",
                new InventoryAdjustmentDto(1, "First", UUID.randomUUID()), getHeadersWith(token), String.class);
        assertThat(rest.getStatusCode().is2xxSuccessful()).isTrue();
        var graphql = executePost("/api/v1/graphql", Map.of("query", "mutation($id:ID!,$input:InventoryAdjustmentInput!)"
                + "{adjustInventory(productId:$id,input:$input){id quantityAfter}}", "variables",
                Map.of("id", product.getId(), "input", Map.of("delta", 2, "reason", "Second", "requestId", UUID.randomUUID().toString()))),
                getHeadersWith(token), String.class);
        var result = mapper.readTree(graphql.getBody());
        assertThat(result.has("errors")).isFalse();
        var latest = result.at("/data/adjustInventory");
        var client = stub(token);

        // when
        var page = client.listStockMovements(ListStockMovementsRequest.newBuilder()
                .setProductId(product.getId()).setSize(1).build());

        // then
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.getItemsList()).singleElement().satisfies(item -> assertThat(item.getId()).isEqualTo(latest.path("id").asLong()));
        assertThat(client.getStock(GetStockRequest.newBuilder().setProductId(product.getId()).build()).getAvailableQuantity())
                .isEqualTo(latest.path("quantityAfter").asInt());
    }


    @Test
    void rejectsExpiredTokensAndUsesCurrentRolesInsteadOfStaleJwtClaims() {
        // given
        var user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        var token = getToken(user);
        var saved = users.findByUsername(user.getUsername()).orElseThrow();
        saved.setRoles(List.of(Role.ROLE_CLIENT));
        users.saveAndFlush(saved);
        var expired = Jwts.builder().subject(user.getUsername()).expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor("test-key".repeat(4).getBytes(StandardCharsets.UTF_8))).compact();

        // when / then
        assertStatus(Status.Code.PERMISSION_DENIED, () -> stub(token).listInventory(ListInventoryRequest.getDefaultInstance()));
        assertStatus(Status.Code.UNAUTHENTICATED, () -> stub(expired).listInventory(ListInventoryRequest.getDefaultInstance()));
    }

    @Test
    void requestIdsAreScopedToEachProductAndStockOverflowIsRejected() {
        // given
        var first = setupProduct();
        var second = setupProduct();
        second.setStockQuantity(Integer.MAX_VALUE);
        productRepository.saveAndFlush(second);
        var client = stub(admin());
        var request = adjustment(first.getId(), -1);

        // when
        var firstMovement = client.adjustStock(request);
        var secondMovement = client.adjustStock(request.toBuilder().setProductId(second.getId()).build());

        // then
        assertThat(firstMovement.getId()).isNotEqualTo(secondMovement.getId());
        assertThat(firstMovement.getRequestId()).isEqualTo(secondMovement.getRequestId());
        assertThat(secondMovement.getQuantityAfter()).isEqualTo(Integer.MAX_VALUE - 1);
        assertStatus(Status.Code.FAILED_PRECONDITION, () -> client.adjustStock(adjustment(second.getId(), 2)));
    }

    @Test
    void inventoryFiltersAndPaginationPreserveTotalsAndStockBoundaries() {
        // given
        var product = setupProduct();
        product.setName("Native inventory fixture");
        product.setCategory("GrpcTraining");
        product.setStockQuantity(5);
        productRepository.saveAndFlush(product);
        var client = stub(admin());
        Map.of("Unrelated fixture", "GrpcTraining", "Native elsewhere", "OtherCategory").forEach((name, category) -> {
            var other = setupProduct();
            other.setName(name);
            other.setCategory(category);
            other.setStockQuantity(5);
            productRepository.saveAndFlush(other);
        });
        var request = ListInventoryRequest.newBuilder().setSize(1).setSearch("native")
                .setCategory("Grpctraining").setStatus(StockStatus.LOW_STOCK).setLowStockThreshold(5);

        // when
        var first = client.listInventory(request.build());
        var next = client.listInventory(request.setPage(1).build());
        var outOfStock = client.listInventory(request.setPage(0).setStatus(StockStatus.OUT_OF_STOCK).build());
        var inStock = client.listInventory(request.setStatus(StockStatus.IN_STOCK).setLowStockThreshold(4).build());

        // then
        assertThat(first.getItemsList()).singleElement().satisfies(item -> {
            assertThat(item.getCategory()).isEqualTo(product.getCategory());
            assertThat(item.getLastChangedAt()).isNotBlank();
            assertThat(item.getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
        });
        assertThat(first.getTotal()).isEqualTo(1);
        assertThat(next.getItemsList()).isEmpty();
        assertThat(next.getTotal()).isEqualTo(1);
        assertThat(next.getPage()).isEqualTo(1);
        assertThat(outOfStock.getItemsList()).isEmpty();
        assertThat(inStock.getItemsCount()).isEqualTo(1);
    }

    @Test
    void healthRequiresAdminReflectionIsOffAndMessageSizeIsBounded() throws Exception {
        // given
        var token = admin();
        var headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        var authentication = MetadataUtils.newAttachHeadersInterceptor(headers);
        var reflectionStatus = new CompletableFuture<Status.Code>();

        // when
        var health = HealthGrpc.newBlockingStub(channel).withInterceptors(authentication)
                .withDeadlineAfter(5, TimeUnit.SECONDS).check(HealthCheckRequest.getDefaultInstance());
        var reflection = ServerReflectionGrpc.newStub(channel).withInterceptors(authentication)
                .withDeadlineAfter(5, TimeUnit.SECONDS).serverReflectionInfo(new StreamObserver<ServerReflectionResponse>() {
                    @Override public void onNext(ServerReflectionResponse response) { reflectionStatus.complete(Status.Code.OK); }
                    @Override public void onError(Throwable failure) { reflectionStatus.complete(Status.fromThrowable(failure).getCode()); }
                    @Override public void onCompleted() { }
                });
        reflection.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
        reflection.onCompleted();

        // then
        assertThat(health.getStatus().name()).isEqualTo("SERVING");
        assertThat(reflectionStatus.get(5, TimeUnit.SECONDS)).isEqualTo(Status.Code.UNIMPLEMENTED);
        assertStatus(Status.Code.RESOURCE_EXHAUSTED, () -> stub(token).adjustStock(adjustment(1, 1).toBuilder()
                .setReason("x".repeat(70000)).build()));
    }

}
